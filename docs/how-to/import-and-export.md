# Import and export collections

Postest can import existing API descriptions and export collections in Postman format.

## Import a collection

1. In the sidebar, open the **Collection actions** menu (`⋮` next to **New collection**).
2. Choose **Import Collection**.
3. Select a `.json`, `.yaml`, or `.yml` file.

Postest detects the format automatically and creates a new collection.

Supported inputs:

- Postman Collection **v1.0**, **v2.0**, and **v2.1**
- OpenAPI **3.x** specifications in JSON or YAML

Folders in the source become nested folders in Postest. Requests are converted to Postest requests with their method, URL, headers, query parameters, body, and authentication where the source format provides them.

## Export a collection

1. Open the collection's `⋮` menu.
2. Choose **Export to Postman**.
3. Pick a destination file.

The result is a Postman Collection **v2.1** JSON file. If you omit the `.json` extension, Postest adds it.

A suggested filename based on the collection name is filled in for you.

Query parameters typed directly into a request URL are exported alongside the parameters in the query editor, so both sets round-trip through import and export. Duplicate keys are preserved, values are decoded once, and URL fragments are not exported.

## Troubleshooting

- **The import fails** - confirm the file is valid JSON/YAML and is a Postman v1/v2 collection or an OpenAPI 3.x document. Unsupported or malformed input is reported as an error. The message deliberately omits the file content and parser details so credentials in a malformed file are not exposed.
- **Requests are missing after import** - check whether they were nested in folders; expand the imported collection.
- **Export cannot write the file** - choose a directory you can write to. The export path is created from the selected file, and Postest reports an error if writing fails.

Logs are written to `~/.postest/logs/postest.log` if you need details; failed imports record only a fixed message, never the parsed content. See [Configuration, files, and limits](../reference/configuration.md).

## Related

- [Supported formats and body types](../reference/supported-formats.md)
- [Organize collections and move items](organize-collections.md)
