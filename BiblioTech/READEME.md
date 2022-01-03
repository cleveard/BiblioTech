# BiblioTech

BiblioTech Android App - An application for tracking books

## Building

- Clone the repository
- You will need to add a project to [Google Cloud Services and get an Api Key](https://developers.google.com/books/docs/v1/using#APIKey) to access Google Books.
- Add a variable to your environment named `GOOGLE_BOOKS_API_KEY` whose value is the API Key you got for Google Books. You will need to add the variable to your user environment, unless you run the build from a command line.
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
