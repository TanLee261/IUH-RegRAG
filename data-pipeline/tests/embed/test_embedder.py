import numpy as np
import pytest

from embed.config import EmbeddingConfig
from embed.embedder import Embedder


class FakeTokenizer:
    def __call__(self, texts, **kwargs):
        return {
            'length': [len(text.split()) + 2 for text in texts],
            'input_ids': [[0] * (len(text.split()) + 2) for text in texts],
        }


class FakeModel:
    def __init__(self, dimension=4, *, invalid=None):
        self.dimension = dimension
        self.invalid = invalid
        self.tokenizer = FakeTokenizer()
        self.max_seq_length = None

    def encode(self, texts, **kwargs):
        vectors = np.zeros((len(texts), self.dimension), dtype=np.float32)
        vectors[:, 0] = 1.0
        if self.invalid == 'shape':
            return vectors[:, :-1]
        if self.invalid == 'nan':
            vectors[0, 0] = np.nan
        if self.invalid == 'norm':
            vectors[:, 0] = 2.0
        return vectors


def make_embedder(model=None):
    config = EmbeddingConfig(
        model_name='test-model',
        dimension=4,
        max_tokens=16,
        batch_size=2,
        device='cpu',
    )
    return Embedder(config, model=model or FakeModel())


def test_embeds_documents_with_expected_shape_and_norm():
    embedder = make_embedder()

    vectors = embedder.embed_documents(['nội dung một', 'nội dung hai'])

    assert vectors.shape == (2, 4)
    assert vectors.dtype == np.float32
    assert np.allclose(np.linalg.norm(vectors, axis=1), 1.0)
    assert embedder.model.max_seq_length == 16


def test_reports_untruncated_token_lengths():
    embedder = make_embedder()

    assert embedder.token_lengths(['một hai', 'một hai ba']) == [4, 5]


def test_empty_document_list_returns_typed_matrix():
    vectors = make_embedder().embed_documents([])

    assert vectors.shape == (0, 4)
    assert vectors.dtype == np.float32


@pytest.mark.parametrize(
    ('invalid', 'message'),
    [('shape', 'shape'), ('nan', 'NaN'), ('norm', 'normalized')],
)
def test_rejects_invalid_model_output(invalid, message):
    embedder = make_embedder(FakeModel(invalid=invalid))

    with pytest.raises(ValueError, match=message):
        embedder.embed_documents(['nội dung'])


def test_rejects_empty_query_and_document():
    embedder = make_embedder()

    with pytest.raises(ValueError, match='query'):
        embedder.embed_query(' ')
    with pytest.raises(ValueError, match='input'):
        embedder.embed_documents([' '])


def test_config_reads_environment(monkeypatch):
    monkeypatch.setenv('EMBEDDING_BATCH_SIZE', '4')
    monkeypatch.setenv('EMBEDDING_DEVICE', 'cpu')

    config = EmbeddingConfig.from_env()

    assert config.batch_size == 4
    assert config.device == 'cpu'
