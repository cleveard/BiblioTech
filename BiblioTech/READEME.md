# BiblioTech

BiblioTech Android App - An application for tracking books

## Building

- Clone the repository
- You will need to add a project to [Google Cloud Services and get an Api Key](https://developers.google.com/books/docs/v1/using#APIKey) to access Google Books. If you want to use OAuth, you will also need to setup and get an OAuth ID.
- Add these variables to your environment. You will need to add the variables to your user environment, unless you run the build from a command line.
  - **GOOGLE_BOOKS_API_KEY** whose value is the API Key you got for Google Books
  - **GOOGLE_BOOKS_OAUTH_ID** whose value is the OAuth id you got for Google Books. Set the value to the empty string if you don't use OAuth.
  - **GOOGLE_BOOKS_REDIRECT_SCHEME** whose value is the OAuth id you got for Google Books, but in reverse DNS notation. So if the id is 123.apps.googleusercontent.com this variable should be set to com.googleusercontent.apps.123. This one is separate from **GOOGLE_BOOKS_OAUTH_ID** because it needs to be set from the environment in the AndroidManifest. *TODO: Use only this variable and convert it to the GOOGLE_BOOKS_OAUTH_ID value.*
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
