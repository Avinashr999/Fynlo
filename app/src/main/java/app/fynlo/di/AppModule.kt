package app.fynlo.di

import android.content.Context
import androidx.room.Room
import app.fynlo.data.AuthManager
import app.fynlo.data.AccountRepository
import app.fynlo.data.BudgetRepository
import app.fynlo.data.RecurringRepository
import app.fynlo.data.ReportRepository
import app.fynlo.data.DebtRepository
import app.fynlo.data.ExpenseRepository
import app.fynlo.data.FinanceRepository
import app.fynlo.data.InvestmentRepository
import app.fynlo.data.LendingRepository
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.MIGRATION_10_11
import app.fynlo.data.local.MIGRATION_11_12
import app.fynlo.data.local.MIGRATION_12_13
import app.fynlo.data.local.MIGRATION_13_14
import app.fynlo.data.local.MIGRATION_3_4
import app.fynlo.data.local.MIGRATION_4_5
import app.fynlo.data.local.MIGRATION_5_6
import app.fynlo.data.local.MIGRATION_6_7
import app.fynlo.data.local.MIGRATION_7_8
import app.fynlo.data.local.MIGRATION_8_9
import app.fynlo.data.local.MIGRATION_9_10
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides all app-level dependencies.
 *
 * Replaces the manual wiring in FynloApplication.onCreate().
 * Dependencies are now injected by Hilt rather than instantiated manually.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FynloDatabase =
        Room.databaseBuilder(context, FynloDatabase::class.java, "Fynlo_database")
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
            )
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()

    @Provides
    @Singleton
    fun provideDao(db: FynloDatabase): FynloDao = db.dao()

    @Provides
    @Singleton
    fun provideAuthManager(): AuthManager = AuthManager()

    @Provides
    @Singleton
    fun provideFirestoreRepository(): FirestoreRepository = FirestoreRepository("")

    @Provides
    @Singleton
    fun provideSyncManager(
        firestoreRepository: FirestoreRepository,
        dao: FynloDao
    ): SyncManager = SyncManager("", dao)

    @Provides @Singleton
    fun provideLendingRepository(dao: FynloDao, fs: FirestoreRepository): LendingRepository =
        LendingRepository(dao, fs)

    @Provides @Singleton
    fun provideDebtRepository(dao: FynloDao, fs: FirestoreRepository): DebtRepository =
        DebtRepository(dao, fs)

    @Provides @Singleton
    fun provideInvestmentRepository(dao: FynloDao, fs: FirestoreRepository): InvestmentRepository =
        InvestmentRepository(dao, fs)

    @Provides @Singleton
    fun provideExpenseRepository(dao: FynloDao, fs: FirestoreRepository): ExpenseRepository =
        ExpenseRepository(dao, fs)

    @Provides @Singleton
    fun provideAccountRepository(dao: FynloDao, fs: FirestoreRepository): AccountRepository =
        AccountRepository(dao, fs)

    @Provides
    @Singleton
    fun provideFinanceRepository(
        dao: FynloDao,
        db: FynloDatabase,
        firestoreRepository: FirestoreRepository,
        syncManager: SyncManager
    ): FinanceRepository = FinanceRepository(dao, db, firestoreRepository, syncManager)
    @Provides @Singleton
    fun provideBudgetRepository(dao: FynloDao, fs: FirestoreRepository): BudgetRepository =
        BudgetRepository(dao, fs)

    @Provides @Singleton
    fun provideRecurringRepository(dao: FynloDao, fs: FirestoreRepository): RecurringRepository =
        RecurringRepository(dao, fs)

    @Provides @Singleton
    fun provideReportRepository(dao: FynloDao, fs: FirestoreRepository): ReportRepository =
        ReportRepository(dao, fs)

}
