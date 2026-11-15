package com.postpole.gharkhata.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.postpole.gharkhata.ads.AdsConfigSource
import com.postpole.gharkhata.ads.RemoteAdsConfigSource
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.createAnalytics
import com.postpole.gharkhata.review.ReviewPolicy
import com.postpole.gharkhata.review.ReviewPrompter
import com.postpole.gharkhata.billing.BillingGateway
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.ui.provider.ProStatus
import com.postpole.gharkhata.billing.PlayBillingGateway
import com.postpole.gharkhata.data.AppDatabase
import com.postpole.gharkhata.data.DayMarkDao
import com.postpole.gharkhata.data.MIGRATION_1_2
import com.postpole.gharkhata.data.MonthSettlementDao
import com.postpole.gharkhata.data.ProviderDao
import com.postpole.gharkhata.data.RateChangeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideProviderDao(db: AppDatabase): ProviderDao = db.providerDao()

    @Provides
    fun provideRateChangeDao(db: AppDatabase): RateChangeDao = db.rateChangeDao()

    @Provides
    fun provideDayMarkDao(db: AppDatabase): DayMarkDao = db.dayMarkDao()

    @Provides
    fun provideMonthSettlementDao(db: AppDatabase): MonthSettlementDao = db.monthSettlementDao()

    @Provides
    @Singleton
    fun provideBillingGateway(@ApplicationContext context: Context): BillingGateway =
        PlayBillingGateway(context)

    @Provides
    @Singleton
    fun provideBillingRepository(
        gateway: BillingGateway,
        dataStore: DataStore<Preferences>,
        analytics: Analytics,
    ): BillingRepository = BillingRepository(
        gateway = gateway,
        dataStore = dataStore,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        analytics = analytics,
    )

    @Provides
    @Singleton
    fun provideProStatus(billing: BillingRepository): ProStatus = object : ProStatus {
        override val isPro = billing.isPro
    }

    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics = createAnalytics(context)

    @Provides
    @Singleton
    fun provideAdsConfigSource(@ApplicationContext context: Context): AdsConfigSource =
        RemoteAdsConfigSource(context)

    @Provides
    fun provideReviewPrompter(policy: ReviewPolicy): ReviewPrompter = policy

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
