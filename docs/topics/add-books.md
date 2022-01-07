---
title: "Adding Books"
---

# Adding books

Books are added to the book list using the Scan activity. You can add books by scanning ISBN barcode, entering ISBN numbers manually or searching by author and/or title. You can also select the tags assigned when you add a book.

<details><summary><li>Opening the Scan Activity</li></summary>
<p><a id="open-scan"></a>

- Open the navigation menu

     ![Navigation Button](../images/nav-button.png)

- Select **Scan**

     ![Scan](../images/scan.png)
</details>
<details><summary><li>Setting tags for the added book(s)</li></summary>
<p><a id="setting-tags"></a>

- Tags are assigned when you add a book. You can touch in the **Selected Tags** field to add and remove tags for the next book added. You are only allowed to add existing tags. See [Managing Tags](tags.html) to learn how to add tags to BiblioTech.

     ![Selected Tags](../images/scan-tags.png)
</details>
<details><summary><li>Scanning the books ISBN barcode</li></summary>
<p><a id="scan-barcode"></a>

- BiblioTech scans barcodes using the phone camera. It will ask you for permission to use the camera. If you don't grant permission, you won't be able to scan barcodes. You can still add books manually by [entering the ISBN](#manual-isbn) or [entering the author and/or title](#manual-author-title).
- Position the phone so the book's barcode is visible on the display
- Press either the up or down volume button
- When the barcode is read, it will be displyed on the **ISBN** line
- When the book is looked up, the title and author(s) will appear on the **Title** and **Authors** line
</details>
<details><summary><li>Entering the ISBN number</li></summary>
<p><a id="manual-isbn"></a>

- Touch the **ISBN** line and enter the number without dashes or spaces. If the ISBN number has an **X** as the last digit, enter an **\***

     ![Enter ISBN](../images/isbn-enter.png)

- Make sure the **Author** and **Title** lines are empty
- Press the search button

     ![Search Button](../images/search-button.png)
     
- When the book is looked up, the title and author(s) will appear on the **Title** and **Authors** line
</details>
<details><summary><li>Searching for the books title and/or author</li></summary>
<p><a id="manual-author-title"></a>

- Use the **Title** and **Author** lines to enter the title and/or author you want to search for.

     ![Search Title And Author](../images/title-author-enter.png)

- Press the search button

     ![Search Button](../images/search-button.png)
     
- Usually this search will find multiple books that match the title and author, so the results are displayed in a dialog
- Touch the book image to select the book(s) that you want to add
- The image is replaced by a star for selected books
- You can filter the results using the **Author** and **Title** lines
- Selected books are not filtered
- Press OK to add the selected books
- You can add multiple books using this method

     ![Search Result](../images/search-results.png)
</details>
<p>

- If an error occurs, a popup will let you know.
- Some barcodes on books are UPC barcodes and BiblioTech can't look those up. The ISBN number is usually printed along with the barcode. Match the barcode number with the ISBN number to scan the correct barcode. If there isn't an ISBN barcode [you can enter it manually](#manual-isbn).
- If the book cannot be found by ISBN, you can [search for the book using the title and/or author](#manual-author-title).
- Once the book is found, it will be selected when adding it to your book list. You can [change the tags](tags.html), if you forgot to set them correctly before scanning (something I do quite frequently).
