# BiblioTech

BiblioTech Android App - An application for tracking books

## Building

- Clone the repository
- You will need to add a project to [Google Cloud Services and get an Api Key](https://developers.google.com/books/docs/v1/using#APIKey) to access Google Books. If you want to use OAuth, you will also need to setup and get an OAuth ID. You need to create an OAuth ID for the debug and release builds that you want to run. The package id and SHA-1 certificate fingerprint are different for the two builds.
- Add these variables to your environment. You will need to add the variables to your user environment.
  - **GOOGLE_BOOKS_API_KEY** whose value is the API Key you got for Google Books
  - **GOOGLE_BOOKS_OAUTH_ID_DEBUG** whose value is the OAuth id you got for Google Books for the debug application.
  - **GOOGLE_BOOKS_OAUTH_ID_RELEASE** whose value is the OAuth id you got for Google Books for the release application.
  - **GOOGLE_BOOKS_REDIRECT_SCHEME_DEBUG** whose value is the OAuth id you got for Google Books for the debug application, but in reverse DNS notation. So if the id is 123.apps.googleusercontent.com this variable should be set to com.googleusercontent.apps.123. This one is separate from **GOOGLE_BOOKS_OAUTH_ID_DEBUG** because it needs to be set from the environment in the AndroidManifest. *TODO: Use only this variable and convert it to the GOOGLE_BOOKS_OAUTH_ID value.*
  - **GOOGLE_BOOKS_REDIRECT_SCHEME_RELEASE** whose value is the OAuth id you got for Google Books for the release application, but in reverse DNS notation. So if the id is 123.apps.googleusercontent.com this variable should be set to com.googleusercontent.apps.123. This one is separate from **GOOGLE_BOOKS_OAUTH_ID_RELEASE** because it needs to be set from the environment in the AndroidManifest. *TODO: Use only this variable and convert it to the GOOGLE_BOOKS_OAUTH_ID value.*
- Add these variables to ~/.gradle/gradle.properties
>**BIBLIOTECH_KEY_PASSWORD**=*Your-key-password*<br>
>**BIBLIOTECH_SIGN_STORE_FILE**=*Path-to-your-keystore*<br>
>**BIBLIOTECH_STORE_PASSWORD**=*Your-key-store-password*<br>
- You can set these variables to empty strings if you don't build release builds.
- Build the file using Android Studio

## TODO

### Book Acquisition
- [x] Add search for Google books
- [x] Add voice for search
- [ ] Add other book databases
- [x] Add Sorting

### Sorting
- [x] Add sorting for SQL

### Filtering
- [x] Add filtering

### UI
- [ ] Clean up options menu
- [x] Add book views based on filtering
- [ ] Special handling for single tag filter views
