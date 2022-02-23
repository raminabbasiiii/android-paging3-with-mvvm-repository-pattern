package com.raminabbasiiii.paging3.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.raminabbasiiii.paging3.data.db.AppDatabase
import com.raminabbasiiii.paging3.data.db.movie.MovieEntity
import com.raminabbasiiii.paging3.data.db.remotekey.RemoteKey
import com.raminabbasiiii.paging3.data.network.Api
import com.raminabbasiiii.paging3.data.network.toMovieEntity
import com.raminabbasiiii.paging3.util.Constants.Companion.STARTING_PAGE_INDEX
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@ExperimentalPagingApi
class MovieRemoteMediator
@Inject
constructor(
    private val api: Api,
    private val database: AppDatabase
    ): RemoteMediator<Int, MovieEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MovieEntity>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> {
                    if (database.movieDao().count() > 0)
                        return MediatorResult.Success(false)
                    null
                }
                LoadType.PREPEND ->
                    return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    getKey()
                }
            }

            if (loadKey != null) {
                if (loadKey.isEndReached)
                    return MediatorResult.Success(endOfPaginationReached = true)
            }

            val page: Int = loadKey?.nextKey ?: STARTING_PAGE_INDEX
            val response = api.getAllMovies(page)

            val movieList = response.data.map { it.toMovieEntity() }

            val endOfPaginationReached =
                page == response.metaData.pageCount

            database.withTransaction {

                if (loadType == LoadType.REFRESH) {
                    database.movieDao().deleteAllMovies()
                   database.remoteKeyDao().deleteAllKeys()
                }

                val nextKey = page + 1

               database.remoteKeyDao().insertKey(
                    RemoteKey(
                        0,
                        nextKey = nextKey,
                        isEndReached = endOfPaginationReached
                    )
                )
                database.movieDao().insertAllMovies(movieList)
            }
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun getKey(): RemoteKey? {
        return database.remoteKeyDao().getKeys().firstOrNull()
    }

}