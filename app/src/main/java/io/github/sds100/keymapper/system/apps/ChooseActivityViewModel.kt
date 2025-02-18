package io.github.sds100.keymapper.system.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.sds100.keymapper.util.State
import io.github.sds100.keymapper.util.filterByQuery
import io.github.sds100.keymapper.util.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/**
 * Created by sds100 on 27/01/2020.
 */
class ChooseActivityViewModel(private val useCase: DisplayAppsUseCase) : ViewModel() {

    val searchQuery = MutableStateFlow<String?>(null)

    private val unfilteredListItems: Flow<State<List<ActivityListItem>>> =
        useCase.installedPackages.map { packagesState ->

            if (packagesState !is State.Data) {
                return@map State.Loading
            }

            val listItems = sequence {
                packagesState.data.forEach { packageInfo ->

                    val appName =
                        useCase.getAppName(packageInfo.packageName).valueOrNull() ?: return@forEach

                    val appIcon =
                        useCase.getAppIcon(packageInfo.packageName).valueOrNull()

                    packageInfo.activities.forEach { activityInfo ->
                        yield(
                            ActivityListItem(
                                appName = appName,
                                activityInfo = activityInfo,
                                icon = appIcon
                            )
                        )
                    }
                }
            }

            val sortedListItems = listItems.toList().sortedBy { it.appName }

            return@map State.Data(sortedListItems)
        }.flowOn(Dispatchers.Default)

    private val _listItems = MutableStateFlow<State<List<ActivityListItem>>>(State.Loading)
    val listItems = _listItems.asStateFlow()


    init {
        combine(
            searchQuery,
            unfilteredListItems
        ) { searchQuery, unfilteredListItemsState ->

            if (unfilteredListItemsState is State.Data) {
                unfilteredListItemsState.data.filterByQuery(searchQuery).collectLatest {
                    _listItems.value = it
                }
            } else {
                _listItems.value = unfilteredListItemsState
            }

        }.launchIn(viewModelScope)
    }

    class Factory(
        private val useCase: DisplayAppsUseCase
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>) =
            ChooseActivityViewModel(useCase) as T
    }
}
