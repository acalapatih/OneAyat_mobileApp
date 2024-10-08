package com.acalapatih.hafalin.core.data.repositoryImpl.hafalanquran

import com.acalapatih.hafalin.core.data.NetworkOnlyResource
import com.acalapatih.hafalin.core.data.Resource
import com.acalapatih.hafalin.core.data.source.remote.RemoteDataSource
import com.acalapatih.hafalin.core.data.source.remote.network.ApiResponse
import com.acalapatih.hafalin.core.data.source.remote.response.PostSpeechToTextResponse
import com.acalapatih.hafalin.core.domain.model.hafalanquran.SpeechToTextModel
import com.acalapatih.hafalin.core.domain.repository.hafalanquran.SpeechToTextRepository
import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody

class SpeechToTextRepositoryImpl(
    private val remoteDataSource: RemoteDataSource,
): SpeechToTextRepository {
    override fun postSpeechToText(
        requestBody: RequestBody
    ): Flow<Resource<SpeechToTextModel>> =
        object : NetworkOnlyResource<SpeechToTextModel, PostSpeechToTextResponse>() {
            override fun loadFromNetwork(data: PostSpeechToTextResponse): Flow<SpeechToTextModel> =
                SpeechToTextModel.mapResponseToModel(data)

            override suspend fun createCall(): Flow<ApiResponse<PostSpeechToTextResponse>> =
                remoteDataSource.postSpeechToText(requestBody)
        }.asFlow()
}