package io.github.adgjmptw2.diet.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.adgjmptw2.diet.data.DietDatabase
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DietDatabase =
        Room.databaseBuilder(context, DietDatabase::class.java, "diet.db").build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
