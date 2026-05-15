package app.fynlo.di

import android.content.Context
import androidx.room.Room
import app.fynlo.data.AccountRepository
import app.fynlo.data.DebtRepository
import app.fynlo.data.ExpenseRepository
import app.fynlo.data.FinanceRepository
import app.fynlo.data.InvestmentRepository
import app.fynlo.data.LendingRepository
import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.local.MIGRATION_3_4
import app.fynlo.data.local.MIGRATION_4_5
import app.fynlo.data.local.MIGRATION_5_6
import app.fynlo.data.local.MIGRATION_6_7
import app.fynlo.data.local.MIGRATION_7_8
import app.fynlo.data.local.MIGRATION_8_9
import app.fynlo.data.local.MIGRATION_9_10
import app.fynlo.data.local.MIGRATION_10_11
import app.fynlo.data.local.MIGRATION_11_12
import app.fynlo.data.local.MIGRATION_12_13
import app.fynlo.data.local.MIGRATION_13_14
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFynloDatabase(@ApplicationContext context: Context): FynloDatabase {
        return Room.databaseBuilder(context, FynloDatabase::class.java, "Fynlo_database")
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideFynloDao(database: FynloDatabase): FynloDao {
        return database.dao()
    }

    @Provides
    @Singleton
    fun provideFirestoreRepository(): FirestoreRepository {
        return FirestoreRepository("")
    }

    @Provides
    @Singleton
    fun provideFinanceRepository(
        dao: FynloDao,
        database: FynloDatabase,
        firestore: FirestoreRepository
    ): FinanceRepository {
        val syncManager = SyncManager("", dao)
        return FinanceRepository(dao, database, firestore, syncManager)
    }

    @Provides
    @Singleton
    fun provideLendingRepository(dao: FynloDao, firestore: FirestoreRepository): LendingRepository {
        return LendingRepository(dao, firestore)
    }

    @Provides
    @Singleton
    fun provideDebtRepository(dao: FynloDao, firestore: FirestoreRepository): DebtRepository {
        return DebtRepository(dao, firestore)
    }

    @Provides
    @Singleton
    fun provideInvestmentRepository(dao: FynloDao, firestore: FirestoreRepository): InvestmentRepository {
        return InvestmentRepository(dao, firestore)
    }

    @Provides
    @Singleton
    fun provideExpenseRepository(dao: FynloDao, firestore: FirestoreRepository): ExpenseRepository {
        return ExpenseRepository(dao, firestore)
    }

    @Provides
    @Singleton
    fun provideAccountRepository(dao: FynloDao, firestore: FirestoreRepository): AccountRepository {
        return AccountRepository(dao, firestore)
    }
}
