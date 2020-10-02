package com.example.cleve.bibliotech.ui.modes

import android.app.Activity
import android.graphics.Rect
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.reflect.KFunction

/**
 * Base class for modal actions using ActionMode
 * @param title The title for the modal action
 * @param subTitel The sub-title for the modal action
 * @param id The id of the menu to use for the modal action
 * @param actions Array of actions to perform when menu items are selected.
 */
open class ModalAction(protected val title: String, protected val subTitle: String, protected val id: Int, actions: Array<Action>?) : ActionMode.Callback {
    /**
     * Action Item
     * @param id The menu item id for this action
     * @param action The function executed when the menu item is selected.
     *   The prototype is fun callback(MenuItem): Boolean
     */
    data class Action(val id: Int, val action: KFunction<Boolean>)

    /**
     * The array of actions for the modal action
     */
    protected val map: Array<Action> = actions?: emptyArray()

    /**
     * The ActionMode for the model action. This is null is the model action isn't active
     */
    protected var mode: ActionMode? = null

    /**
     * {@inheritdoc}
     * Look up the action in the array of actions and call it. Return false if not found.
     */
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val itemId = item.itemId
        val action = map.firstOrNull() { it.id == itemId }?.action
        return if (action == null) false else action.call(this, item)
    }

    /**
     * {@inheritdoc}
     * Inflate the menu and set it up.
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(id, menu)
        mode.title = title
        mode.subtitle = subTitle
        this.mode = mode
        return true;
    }

    /**
     * {@inheritdoc}
     * Nothing to do here. Subclasses can handle special preperations
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
    }

    /**
     * {@inheritdoc}
     * Clear the ActionMode from this object
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        this.mode = null
    }

    /**
     * Start the modal action.
     * @param activity The current activity
     */
    fun start(activity: Activity?): ActionMode {
        val a = activity as? AppCompatActivity;
        return a?.startSupportActionMode(this)!!
    }

    /**
     * Finish the model action
     */
    fun finish() {
        mode?.finish()
    }
}