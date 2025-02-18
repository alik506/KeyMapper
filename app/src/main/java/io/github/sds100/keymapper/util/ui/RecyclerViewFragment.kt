package io.github.sds100.keymapper.util.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.addRepeatingJob
import androidx.navigation.fragment.findNavController
import androidx.savedstate.SavedStateRegistry
import com.airbnb.epoxy.EpoxyRecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.util.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Created by sds100 on 22/02/2020.
 */
abstract class RecyclerViewFragment<T, BINDING : ViewDataBinding> : Fragment() {

    companion object {
        private const val KEY_SAVED_STATE = "key_saved_state"

        private const val KEY_IS_APPBAR_VISIBLE = "key_is_app_visible"
        private const val KEY_REQUEST_KEY = "key_request_key"
        private const val KEY_SEARCH_STATE_KEY = "key_search_state_key"
    }

    abstract val listItems: Flow<State<List<T>>>

    open var isAppBarVisible = true
    open var requestKey: String? = null
    open var searchStateKey: String? = null

    /**
     * Scoped to the lifecycle of the fragment's view (between onCreateView and onDestroyView)
     */
    private var _binding: BINDING? = null
    val binding: BINDING
        get() = _binding!!

    private val savedStateProvider = SavedStateRegistry.SavedStateProvider {
        Bundle().apply {
            putBoolean(KEY_IS_APPBAR_VISIBLE, isAppBarVisible)
            putString(KEY_REQUEST_KEY, requestKey)
            putString(KEY_SEARCH_STATE_KEY, searchStateKey)
        }
    }

    private val isSearchEnabled: Boolean
        get() = searchStateKey != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedStateRegistry.registerSavedStateProvider(KEY_SAVED_STATE, savedStateProvider)

        savedStateRegistry.consumeRestoredStateForKey(KEY_SAVED_STATE)?.apply {
            isAppBarVisible = getBoolean(KEY_IS_APPBAR_VISIBLE)
            requestKey = getString(KEY_REQUEST_KEY)
            searchStateKey = getString(KEY_SEARCH_STATE_KEY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = bind(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            //initially only show the progress bar
            getProgressBar(binding).isVisible = true
            getRecyclerView(binding).isVisible = true
            getEmptyListPlaceHolderTextView(binding).isVisible = false

            if (searchStateKey != null) {
                findNavController().observeCurrentDestinationLiveData<String>(
                    viewLifecycleOwner,
                    searchStateKey!!
                ) {
                    onSearchQuery(it)
                }
            }

            subscribeUi(binding)

            getBottomAppBar(binding)?.let {
                it.isVisible = isAppBarVisible

                it.setNavigationOnClickListener {
                    onBackPressed()
                }
            }

            setupSearchView(binding)

            if (!requireActivity().onBackPressedDispatcher.hasEnabledCallbacks()) {
                //don't override back button if another fragment is controlling the app bar
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                    if (isAppBarVisible) {
                        onBackPressed()
                    }
                }
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            listItems.collectLatest { state ->
                when (state) {
                    is State.Data -> {
                        if (state.data.isEmpty()) {
                            getProgressBar(binding).isVisible = false

                            /*
                            Use INVISIBLE rather than GONE so that the previous list items don't flash briefly before
                            the new items are populated
                             */
                            getRecyclerView(binding).visibility = View.INVISIBLE
                            getEmptyListPlaceHolderTextView(binding).isVisible = true

                            /*
                             Don't clear the recyclerview here because if a custom epoxy controller is set then
                             it will be cleared which means no items are shown when a request to populate it
                             is made again.
                              */
                            populateList(getRecyclerView(binding), emptyList())
                        } else {
                            getProgressBar(binding).isVisible = true
                            getEmptyListPlaceHolderTextView(binding).isVisible = false

                            /*
                            Don't hide the recyclerview here because if the state changes in response to
                            an onclick event in the recyclerview then there isn't a smooth transition
                            between the states. E.g the ripple effect on a button or card doesn't complete
                             */
                            populateList(getRecyclerView(binding), state.data)

                            getProgressBar(binding).isVisible = false

                            //show the recyclerview once it has been populated
                            getRecyclerView(binding).isVisible = true
                        }
                    }

                    is State.Loading -> {
                        getProgressBar(binding).isVisible = true
                        getRecyclerView(binding).isVisible = false
                        getEmptyListPlaceHolderTextView(binding).isVisible = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        /*
        Don't rebuild UI state in onResume because then EVERY time the fragment is resumed the UI could be updated
        twice. 1st when the state is collected and a 2nd time after the view model has updated it the state
        from the call in onResume even if no configuration has changed. Doing this here
        can cause jank if a list item is complicated.
         */
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    fun returnResult(vararg extras: Pair<String, Any?>) {
        requestKey?.let {
            setFragmentResult(it, bundleOf(*extras))
            findNavController().navigateUp()
        }
    }

    private fun setupSearchView(binding: BINDING) {
        getBottomAppBar(binding) ?: return

        val searchViewMenuItem = getBottomAppBar(binding)!!.menu.findItem(R.id.action_search)
        searchViewMenuItem ?: return

        searchViewMenuItem.isVisible = isSearchEnabled

        val searchView = searchViewMenuItem.actionView as SearchView

        searchStateKey ?: return

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                onSearchQuery(newText)

                return true
            }

            override fun onQueryTextSubmit(query: String?) = onQueryTextChange(query)
        })
    }

    open fun onSearchQuery(query: String?) {}
    open fun getBottomAppBar(binding: BINDING): BottomAppBar? {
        return null
    }

    open fun onBackPressed() {
        findNavController().navigateUp()
    }

    abstract fun getRecyclerView(binding: BINDING): EpoxyRecyclerView
    abstract fun getProgressBar(binding: BINDING): View
    abstract fun getEmptyListPlaceHolderTextView(binding: BINDING): View
    abstract fun subscribeUi(binding: BINDING)
    abstract fun populateList(recyclerView: EpoxyRecyclerView, listItems: List<T>)
    abstract fun bind(inflater: LayoutInflater, container: ViewGroup?): BINDING
}