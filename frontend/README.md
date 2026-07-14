# 2G WMS Admin Frontend

V1 step 1 delivers the React application shell and authentication loop only.

## Local development

1. Start the Spring Boot backend on `http://127.0.0.1:8080`.
2. Install dependencies with `pnpm install`.
3. Regenerate API types after the OpenAPI snapshot changes with `pnpm generate:api`.
4. Start the frontend with `pnpm dev` and open `http://127.0.0.1:5173`.

The Vite development server proxies relative `/api` requests to the backend.

## Quality checks

- `pnpm typecheck`
- `pnpm test`
- `pnpm build`

The production build uses relative asset paths and a hash router, so `dist/` can
be served from a Spring Boot static-resource directory without a history-route
fallback.
