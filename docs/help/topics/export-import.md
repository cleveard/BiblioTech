---
title: Export and Import
---

Export and import use a comma-separated-values (CSV) file to hold the data exported and imported. The first line of the file contains information about the data and is used to decide what is being imported. If you want you can open the file in a spreadsheet.

Opening CSV files in a spreadsheet can reduce the number of digits or convert numeric values to scientific notation. This will cause problems if you want to edit the CSV file and import it back into BiblioTech. BiblioTech prepends a tab character before each number, which causes them to be treated as text in the spreadsheet and prevents them from being changed. Turn off the **Safely edit in spreadsheet** switch stop this behavior.

Some spreadsheets, e.g. Excel, will preserve the tab when the spreadsheet is saved to a CSV. Others, e.g. Google Sheets, will not. This means that if you import the CSV file into Google Sheets, then download the spreadsheet into a CSV file and finally import the downloaded CSV file into Google Sheets or Excel, numbers may be changed.

<details><summary><li>Export Books</li></summary>
<p><a id="export-books"></a>

- Chose the filter to select the books to be exported. **\*\* All Books \*\*** will export all books in your book list. The last filter you were displaying in the book list is chosen by default.
- Use the **Selected Only** switch to only export selected books.
- Press the **Export Books** button

     <img src=../images/export-books.png alt="Import" width="288">
</details>
<details><summary><li>Export Views</li></summary>
<p><a id="export-views"></a>

- Press the **Export Views** button. All saved filters are exported.

     <img src=../images/export-views.png alt="Import" width="288">
</details>
<details><summary><li>Import books or views</li></summary>
<p><a id="import"></a>

- Chose how BiblioTech should handle imported books or views that match books or views you already have.
  - **Keep Existing Books and Views**: Ignore the imported books and views that match
  - **Replace Existing Books and Views**: Replace your existing books and views with the books and views you import
  - **Select Books and Views to Replace Existing**: A dialog is used to select the matching books you want to take from the imported file. Select the books in the dialog you want to import. Unselected books are not changed.

     <img src=../images/import.png alt="Import" width="288">
</details>
