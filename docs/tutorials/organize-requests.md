# Organize requests with collections and environments

In this tutorial you will group requests into folders and use an environment variable for a shared base URL.

## 1. Create a collection

1. In the sidebar, click **New collection**.
2. Enter `Example API` and confirm.

Collections contain requests and folders. You can rename or delete a collection from its `⋮` menu.

## 2. Create folders

1. Open the collection's `⋮` menu and choose **Add Folder**.
2. Name the folder `Users`.
3. Repeat to create a second folder, `Posts`.

Folders can contain requests and other folders. To add a nested folder, use the folder's own `⋮` menu.

## 3. Add requests

For each request you want to keep:

1. Open or create a request tab.
2. Open the collection's `⋮` menu and choose **Add Request**.
3. Give it a name and confirm.

The request is added to the collection root. You can then drag it into a folder:

- drag the request row over a **folder header** and release to move it inside;
- drag it over the **collection header** and release to move it back to the root.

Valid drop targets highlight while you drag. Dropping anywhere else cancels the move.

## 4. Create an environment

1. At the top of the sidebar, open the environment selector and choose **Manage Environments**.
2. Create an environment named `Example`.
3. Add a variable:
   - key: `baseUrl`
   - value: `https://jsonplaceholder.typicode.com`

4. Select the `Example` environment in the selector.

## 5. Use the variable

Open a saved request and set its URL to:

```
{{baseUrl}}/users/1
```

Send it. Postest replaces `{{baseUrl}}` with the value from the active environment.

Variables work in URLs, query parameters, headers, bodies, authentication fields, and client-certificate paths.

## 6. Check history

Open the **History** tab in the sidebar. Each request you send is recorded there; click an entry to reopen the request and its response.

## Next steps

- [Manage environments and variables](../how-to/manage-environments.md)
- [Organize collections and move items](../how-to/organize-collections.md)
- [Variables reference](../reference/variables.md)
