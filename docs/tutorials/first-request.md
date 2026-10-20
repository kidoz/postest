# Send your first request

In this tutorial you will send a GET request, inspect the response, and save the request to a collection.

You need Postest running and an internet connection for the example URL. If you have not installed it yet, see [Build and run from source](../how-to/build-and-run.md) or download a release from GitHub.

## 1. Open Postest

When the app starts, you get one empty request tab. The sidebar on the left has two sections:

- **Collections** - saved requests and folders
- **History** - requests you have already sent

The request editor on the right has `Params`, `Headers`, `Body`, and `Auth` tabs.

## 2. Choose the method and URL

1. Leave the method as **GET**.
2. Click the URL field and enter:

   ```
   https://jsonplaceholder.typicode.com/todos/1
   ```

3. Press **Send** (the send button next to the URL) or **Ctrl/Cmd+Enter**.

## 3. Read the response

The response panel shows:

- the status code and status text (for example `200 OK`),
- the response time,
- the response size,
- the formatted response body.

Use the **Body** and **Headers** tabs to switch views. Click the timing badge to see the DNS, connect, TLS, first-byte, and download breakdown.

## 4. Send a POST with a JSON body

1. Change the method to **POST**.
2. Set the URL to:

   ```
   https://jsonplaceholder.typicode.com/posts
   ```

3. Open the **Body** tab and choose **JSON**.
4. Enter:

   ```json
   {
     "title": "Hello Postest",
     "body": "My first request",
     "userId": 1
   }
   ```

5. Send the request and check the response.

## 5. Save the request to a collection

1. In the sidebar, click **New collection** and name it `Learning`.
2. Open the collection's `⋮` menu and choose **Add Request**.
3. Give the request a name, such as `Create post`, and confirm.

The active tab's request is saved into the collection. Open the collection item to load it again later.

## Next steps

- [Organize requests with collections and environments](organize-requests.md)
- [Import and export collections](../how-to/import-and-export.md)
- [Keyboard shortcuts](../reference/keyboard-shortcuts.md)
