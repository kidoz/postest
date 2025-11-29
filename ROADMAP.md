# Postest - Feature Roadmap

This document outlines planned features and improvements for Postest.

## Current Features

- [x] Full HTTP request building (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS)
- [x] Request body types: JSON, XML, Form URL Encoded, Form Data, Binary, Raw
- [x] Authentication: None, Basic, Bearer Token, API Key
- [x] Response viewing with syntax highlighting (JSON, XML, HTML)
- [x] Collection management with folders
- [x] Environment variables with secret support
- [x] Request history tracking
- [x] Import: Postman Collection (v1, v2, v2.1), OpenAPI 3.x
- [x] Export: Postman Collection v2.1
- [x] Dark theme support
- [x] Tab-based interface
- [x] Response timing breakdown (TTFB, download time)

---

## High Priority

Complete existing backend features that are missing UI:

- [x] FormData Body UI - Add multipart/form-data editor with file upload
- [x] Binary File Upload UI - Add file picker for binary body type
- [ ] GraphQL UI - Add query/variables editor for GraphQL requests
- [ ] Collection-Level Auth UI - UI to set/edit auth at collection level

---

## Medium Priority

Essential features for daily use:

- [ ] cURL Import/Export - Import from cURL command, generate cURL for requests
- [ ] Code Generation - Generate code snippets (Python, JavaScript, Java, Go, etc.)
- [ ] Request Search - Search across collections by name, URL, method
- [ ] Keyboard Shortcuts - Ctrl+Enter to send, Ctrl+N new tab, Ctrl+S save, etc.
- [ ] Request/Response Size - Show request body size and response size
- [ ] Duplicate Request - Duplicate existing request in collection
- [ ] Move Request - Move request between collections/folders
- [ ] Drag & Drop Reorder - Reorder items within collections

---

## Important Features

Advanced HTTP and configuration:

- [ ] OAuth 2.0 Auth - Support OAuth 2.0 flows (Authorization Code, Client Credentials, etc.)
- [ ] Proxy Settings - Configure HTTP/HTTPS proxy
- [ ] SSL Settings - Toggle SSL verification, custom CA certificates
- [ ] Request Timeout - Configure request timeout per request or globally
- [ ] Follow Redirects Toggle - Option to enable/disable auto-follow redirects
- [ ] Cookie Management - View/edit/manage cookies
- [ ] Default Headers - Configure default headers for all requests
- [ ] OpenAPI Export - Export collections to OpenAPI/Swagger format

---

## Nice to Have

Quality of life improvements:

- [ ] Pre-request Scripts - JavaScript/Kotlin scripts before request
- [ ] Post-response Tests - Assertions and tests on response
- [ ] Collection Runner - Run all requests in collection sequentially
- [ ] Environment File Import - Import variables from .env files
- [ ] Response Schema Validation - Validate response against JSON Schema
- [ ] Request Templates - Create reusable request templates
- [ ] Favorites/Pinned Requests - Pin frequently used requests
- [ ] WebSocket Support - Basic WebSocket connection testing
- [ ] Request Comparison - Compare two responses side-by-side
- [ ] Bulk Edit - Bulk edit headers, params across requests

---

## Advanced Features

Future enhancements:

- [ ] API Documentation Viewer - Render OpenAPI specs as documentation
- [ ] Mock Server - Create mock responses for testing
- [ ] Request Chaining - Link requests, pass data between them
- [ ] Performance Testing - Load testing with iterations
- [ ] Team Collaboration - Share collections (cloud sync)
- [ ] Request Versioning - Track changes to requests over time
- [ ] CLI Mode - Run collections from command line
- [ ] Plugin System - Extensible plugin architecture

---

## Technical Improvements

Internal enhancements:

- [ ] Settings Screen - Centralized settings management
- [ ] Undo/Redo - Undo changes in editors
- [ ] Auto-save - Auto-save request changes
- [ ] Request Validation - Validate URL, headers before sending
- [ ] Better Error Messages - More descriptive error handling
- [ ] Response Caching - Cache responses for offline viewing
- [ ] Database Backup - Export/import entire database
- [ ] Localization - Multi-language support

---

## Contributing

Contributions are welcome! Please see the issues labeled `enhancement` for features that are ready to be implemented.
