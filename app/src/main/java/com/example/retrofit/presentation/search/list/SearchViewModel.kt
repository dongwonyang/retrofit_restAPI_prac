package com.example.retrofit.presentation.search.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.retrofit.data.repository.SearchRepositoryImpl
import com.example.retrofit.domain.search.SearchGetUseCase
import com.example.retrofit.domain.search.model.SearchImageEntity
import com.example.retrofit.domain.search.model.SearchVideoEntity
import com.example.retrofit.network.RetrofitClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchGetUseCase
): ViewModel() {

    private val _uiState = MutableStateFlow(SearchListUiState.init())
    val uiState: StateFlow<SearchListUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<SearchListEvent>()
    val event:SharedFlow<SearchListEvent> = _event.asSharedFlow()
    fun onSearch(
        query: String
    ) = viewModelScope.launch {
        showLoading(true)
        runCatching {
            val items = createItems(
                images = searchUseCase.imageGet(query),
                videos = searchUseCase.videoGet(query)
            )

            _uiState.update { prevState ->
                prevState.copy(
                    list = items,
                    isLoading = false
                )
            }
        }.onFailure {
            // network, error, ...
            Log.e("jess", it.message.toString())
            showLoading(false)
        }
    }

    fun onBookmark(
        item:SearchListItem
    ) = viewModelScope.launch{
        val mutableList = uiState.value.list.toMutableList()

        val position = mutableList.indexOfFirst {
            it.id == item.id
        }

        _uiState.update { prev->
            prev.copy(
                list = mutableList.also {
                    it[position] = when(item){
                        is SearchListItem.ImageItem -> item.copy(
                            bookmarked = item.bookmarked.not()
                        )
                        is SearchListItem.VideoItem -> item.copy(
                            bookmarked = item.bookmarked.not()
                        )
                    }
                }
            )
        }

        _event.emit(SearchListEvent.UpdateBookmark(uiState.value.list))
    }

    private fun showLoading(isLoading: Boolean){
        _uiState.update{ prevState ->
            prevState.copy(
                isLoading = isLoading
            )
        }
    }

    private fun createItems(
        images: SearchImageEntity,
        videos: SearchVideoEntity
    ): List<SearchListItem>{

        fun createImageItems(
            images: SearchImageEntity
        ): List<SearchListItem.ImageItem> = images.documents?.map{ document ->
            SearchListItem.ImageItem(
                id = document.id,
                title = document.displaySitename,
                thumbnail = document.thumbnailUrl,
                date = document.datetime,
                url = document.docUrl
            )
        }.orEmpty()

        fun createVideoItems(
            videos: SearchVideoEntity
        ): List<SearchListItem.VideoItem> = videos.documents?.map{document ->
            SearchListItem.VideoItem(
                id = document.id,
                title = document.author,
                thumbnail = document.thumbnail,
                date = document.datetime,
                url = document.url
            )
        }.orEmpty()

        return arrayListOf<SearchListItem>().apply{
            addAll(createImageItems(images))
            addAll(createVideoItems(videos))
        }.sortedByDescending {
            it.date
        }
    }


}
