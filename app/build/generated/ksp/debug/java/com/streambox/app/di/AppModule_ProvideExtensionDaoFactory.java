package com.streambox.app.di;

import com.streambox.app.data.local.AppDatabase;
import com.streambox.app.data.local.ExtensionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class AppModule_ProvideExtensionDaoFactory implements Factory<ExtensionDao> {
  private final Provider<AppDatabase> databaseProvider;

  public AppModule_ProvideExtensionDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ExtensionDao get() {
    return provideExtensionDao(databaseProvider.get());
  }

  public static AppModule_ProvideExtensionDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new AppModule_ProvideExtensionDaoFactory(databaseProvider);
  }

  public static ExtensionDao provideExtensionDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideExtensionDao(database));
  }
}
