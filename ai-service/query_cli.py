"""Inspect the normalized retrieval inputs for one user query."""

import argparse
from dataclasses import asdict
import json

from retrieval import understand_query


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('query')
    result = understand_query(parser.parse_args().query)
    print(json.dumps(asdict(result), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
