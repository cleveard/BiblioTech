package com.github.cleveard.bibliotech.ui.bookshelves

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.RecyclerView
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.ManageNavigation
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.ui.gb.GoogleBookLoginFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookshelvesFragment : Fragment() {

    companion object {
        fun newInstance() = BookshelvesFragment()
    }

    private val shelvesViewModel: BookshelvesViewModel by activityViewModels()

    /**
     * Coroutine job for handling the pager flow
     */
    private lateinit var pagerJob: Job

    /**
     * Tag recycler adapter
     */
    internal val adapter: BookshelvesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val content = inflater.inflate(R.layout.fragment_bookshelves, container, false)
        // Setup the bookshelf recycler view
        setupRecyclerView(content)
        // Setup the fragment action menu. This fragment creates a separate toolbar
        // for its action menu
        setupActionMenu()

        return content
    }

    /**
     * @inheritDoc
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make sure we are logged in
        GoogleBookLoginFragment.login(this)
    }

    /**
     * @inheritDoc
     */
    override fun onDestroyView() {
        // Cancel the pager flow job
        pagerJob.cancel()
        super.onDestroyView()
    }

    /**
     * Setup the recycler view for tags
     */
    private fun setupRecyclerView(content: View) {
        // Setup the pager for the tag recycler view
        val config = PagingConfig(pageSize = 20)
        val pager = Pager(
            config
        ) {
            shelvesViewModel.repo.getShelves()
        }
        val adapter = object: BookshelvesAdapter(shelvesViewModel.viewModelScope) {
            override suspend fun toggleTagAndBookshelfLink(bookshelfId: Long) {
                shelvesViewModel.toggleTagAndBookshelfLink(bookshelfId)
            }

            override suspend fun onRefreshClicked(shelf: BookshelfAndTag, button: MaterialButton) {
                shelvesViewModel.refreshShelfAndBooks(requireActivity() as BookCredentials, shelf, true)
            }
        }
        // Setup the flow for the pager
        val flow = pager.flow
            .cachedIn(shelvesViewModel.viewModelScope)
        pagerJob = shelvesViewModel.viewModelScope.launch {
            flow.collectLatest { data -> adapter.submitData(data)
            }
        }
        // Set the layout manager and adapter to the recycler view
        val shelves = content.findViewById<RecyclerView>(R.id.bookshelves)
        shelves.adapter = adapter
    }


    /**
     * Perform an action when a menu item or button is clicked
     * @param id The id of the menu item or button
     */
    private fun onActionSelected(id: Int): Boolean {
        when (id) {
            // Refresh bookshelves list
            R.id.action_refresh -> {
                shelvesViewModel.viewModelScope.launch {
                    shelvesViewModel.refreshBookshelves(requireActivity() as  BookCredentials)
                }
            }
            // Undo an action
            R.id.action_undo -> {
                shelvesViewModel.viewModelScope.launch {
                    shelvesViewModel.repo.undo()
                }
            }
            // Redo an undone action
            R.id.action_redo -> {
                shelvesViewModel.viewModelScope.launch {
                    shelvesViewModel.repo.redo()
                }
            }
            // Bring up the settings fragment
            R.id.action_to_settingsFragment -> {
                (activity as? ManageNavigation)?.navigate(
                    BookshelvesFragmentDirections.actionBookshelvesFragmentToSettingsFragment()
                )
            }
            // Login/Logout
            R.id.action_logout -> {
                (activity as? BookCredentials)?.let {credentials ->
                    shelvesViewModel.viewModelScope.launch {
                        if (credentials.isAuthorized)
                            credentials.logout()
                        else
                            credentials.login()
                    }
                }
            }
            else -> return false
        }
        return true
    }
        /**
     * Setup the action menu in a separate toolbar
     */
    private fun setupActionMenu() {
        shelvesViewModel.undoList.observe(viewLifecycleOwner) { }

        // Add options menu provider
        requireActivity().addMenuProvider(
            object: MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    // Inflate the menu
                    menuInflater.inflate(R.menu.bookshelf_options, menu)
                }

                /**
                 * @inheritDoc
                 */
                override fun onPrepareMenu(menu: Menu) {
                    // Method to update description and state of menu items
                    fun update(id: Int, desc: String?, undoId: Int, undoNameId: Int) {
                        menu.findItem(id)?.let {item ->
                            item.isEnabled = desc != null
                            item.title = if (desc.isNullOrEmpty())
                                requireContext().getString(undoId)
                            else
                                requireContext().getString(undoNameId, desc)
                        }
                    }
                    // Enable/Disable Undo item and set the string
                    update(R.id.action_undo, shelvesViewModel.undoList.value?.lastOrNull { !it.isRedo }?.desc,
                        R.string.undo, R.string.undoName)
                    // Enable/Disable Redo item and set the string
                    update(R.id.action_redo, shelvesViewModel.undoList.value?.firstOrNull { it.isRedo }?.desc,
                        R.string.redo, R.string.redoName)
                    // Set the Login/Logout string
                    update(
                        R.id.action_logout,
                        "",
                        if ((requireActivity() as? BookCredentials)?.isAuthorized == true)
                            R.string.logout
                        else
                            R.string.login,
                        0)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Direct action to onActionSelected
                    return onActionSelected(menuItem.itemId)
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }
}
