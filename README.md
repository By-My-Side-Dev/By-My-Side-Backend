# gyeote-backend
곁에 프로젝트 백엔드 저장소

## API 문서

- REST API: 애플리케이션 실행 후 `/swagger-ui/index.html`
- STOMP WebSocket API UI: 애플리케이션 실행 후 `/asyncapi/`
- AsyncAPI 원본: [`docs/asyncapi.yaml`](docs/asyncapi.yaml)

AsyncAPI HTML 문서를 로컬에서 다시 생성하려면 Node.js가 설치된 환경에서 실행합니다.

```bash
./script/generate-asyncapi-docs.sh
```

배포 워크플로에서는 위 스크립트를 실행한 뒤 애플리케이션을 빌드합니다.
