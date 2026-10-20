# Manage environments and variables

Environments store reusable values such as base URLs, tokens, and user IDs.

## Open the environment manager

1. Open the environment selector at the top of the sidebar.
2. Choose **Manage Environments**.

You can also click the settings button next to the selector.

## Create an environment

1. Choose **New Environment** (or the add action in the environment manager).
2. Enter a name.
3. Add variables with **Add variable**:
   - **Key** - the name used inside `{{ }}`
   - **Value** - the replacement text
   - **Enabled** - disabled variables are ignored
   - **Secret** - masks the value in the UI
4. Save the environment.

## Activate an environment

Open the environment selector and choose an environment. Choosing **No Environment** disables substitution.

Only the active environment is used when a request is sent.

## Use variables

Reference a variable with double braces:

```
{{baseUrl}}/v1/orders/{{orderId}}
```

Variables are resolved in:

- the request URL
- query parameter keys and values
- header keys and values
- JSON, XML, raw, form, multipart, and GraphQL request bodies
- Basic, Bearer, and API-key authentication fields
- client-certificate paths and passphrase

If a variable is missing, the original `{{name}}` text is sent unchanged. Disabled variables are not resolved.

Dynamic variables are also supported, for example `{{$uuid}}`, `{{$timestamp}}`, `{{$randomInt}}`, and `{{$randomEmail}}`. See the [variables reference](../reference/variables.md).

## Edit or delete an environment

1. Open **Manage Environments**.
2. Select the environment.
3. Change its name or variables and save, or use the delete action.

Saving edits preserves the current active selection, even when you edit an environment that is not active or an older draft of the active one. Use the environment selector or **Set Active** to change which environment is used for requests.

Deleting the active environment clears the active selection.

## Secrets

Secret variables are masked in the environment editor. Their values, like all other stored request and environment data, are encrypted at rest. See the [security model](../explanation/security-model.md).

## Related

- [Variables reference](../reference/variables.md)
- [Authenticate requests](authenticate-requests.md)
- [Tutorial: organize requests with collections and environments](../tutorials/organize-requests.md)
