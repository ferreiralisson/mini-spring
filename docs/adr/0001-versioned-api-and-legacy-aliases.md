# Versioned API with legacy aliases

Publish the course contract under `/api/v1` and keep the existing `/cursos` routes as compatible aliases, so the API can evolve without breaking existing clients. Pagination is introduced only on the versioned list response; the unversioned list retains its array shape.
