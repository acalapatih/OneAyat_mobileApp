package com.acalapatih.oneayat.core.data.repositoryImpl.hafalanquran

import com.acalapatih.oneayat.core.data.NetworkOnlyResource
import com.acalapatih.oneayat.core.data.Resource
import com.acalapatih.oneayat.core.data.source.local.LocalDataSource
import com.acalapatih.oneayat.core.data.source.remote.RemoteDataSource
import com.acalapatih.oneayat.core.data.source.remote.network.ApiResponse
import com.acalapatih.oneayat.core.data.source.remote.response.GetListAyatResponse
import com.acalapatih.oneayat.core.domain.model.hafalanquran.HafalanSuratModel
import com.acalapatih.oneayat.core.domain.repository.hafalanquran.HafalanSuratRepository
import kotlinx.coroutines.flow.Flow

class HafalanSuratRepositoryImpl(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
): HafalanSuratRepository {
    override fun getListAyat(nomorSurat: String): Flow<Resource<HafalanSuratModel>> =
        object : NetworkOnlyResource<HafalanSuratModel, GetListAyatResponse>() {
            override fun loadFromNetwork(data: GetListAyatResponse): Flow<HafalanSuratModel> =
                HafalanSuratModel.GetListAyat.mapResponseToModel(data)

            override suspend fun createCall(): Flow<ApiResponse<GetListAyatResponse>> =
                remoteDataSource.getListAyatSurat(nomorSurat)
        }.asFlow()

    override fun setToken(token: String) = localDataSource.setToken(token)
}