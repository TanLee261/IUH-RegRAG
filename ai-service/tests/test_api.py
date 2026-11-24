from fastapi.testclient import TestClient

from api import create_app


class FakePipeline:
    def __init__(self):
        self.questions = []
        self.closed = False

    def answer(self, question):
        self.questions.append(question)
        return {
            'answer': 'Trả lời có nguồn.',
            'status': 'answered',
            'provider': 'fake',
            'model': 'fake-model',
            'source_ids': ['chunk-1'],
            'cited_source_ids': ['chunk-1'],
            'fallback_used': False,
            'sources': [{'chunk_id': 'chunk-1', 'title': 'Quy chế'}],
        }

    def close(self):
        self.closed = True


def test_answer_endpoint_validates_and_trims_question():
    pipeline = FakePipeline()
    with TestClient(create_app(lambda: pipeline)) as client:
        response = client.post('/answer', json={'question': '  Điều 19 là gì?  '})

    assert response.status_code == 200
    assert response.json()['sources'][0]['chunk_id'] == 'chunk-1'
    assert pipeline.questions == ['Điều 19 là gì?']
    assert pipeline.closed is True


def test_answer_endpoint_rejects_invalid_questions():
    with TestClient(create_app(FakePipeline)) as client:
        assert client.post('/answer', json={}).status_code == 422
        assert client.post('/answer', json={'question': '   '}).status_code == 422
        assert client.post('/answer', json={'question': 'x' * 2001}).status_code == 422


def test_health_does_not_require_model_call():
    pipeline = FakePipeline()
    with TestClient(create_app(lambda: pipeline)) as client:
        assert client.get('/health').json() == {'status': 'ok'}
    assert pipeline.questions == []
