package com.streambox.app.extension;

import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.streambox.app.data.local.ExtensionDao;
import com.streambox.app.runtime.ExtensionExecutor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ExtensionManager_Factory implements Factory<ExtensionManager> {
  private final Provider<Context> contextProvider;

  private final Provider<ExtensionDao> extensionDaoProvider;

  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<ExtensionExecutor> executorProvider;

  public ExtensionManager_Factory(Provider<Context> contextProvider,
      Provider<ExtensionDao> extensionDaoProvider, Provider<OkHttpClient> httpClientProvider,
      Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<ExtensionExecutor> executorProvider) {
    this.contextProvider = contextProvider;
    this.extensionDaoProvider = extensionDaoProvider;
    this.httpClientProvider = httpClientProvider;
    this.dataStoreProvider = dataStoreProvider;
    this.executorProvider = executorProvider;
  }

  @Override
  public ExtensionManager get() {
    return newInstance(contextProvider.get(), extensionDaoProvider.get(), httpClientProvider.get(), dataStoreProvider.get(), executorProvider.get());
  }

  public static ExtensionManager_Factory create(Provider<Context> contextProvider,
      Provider<ExtensionDao> extensionDaoProvider, Provider<OkHttpClient> httpClientProvider,
      Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<ExtensionExecutor> executorProvider) {
    return new ExtensionManager_Factory(contextProvider, extensionDaoProvider, httpClientProvider, dataStoreProvider, executorProvider);
  }

  public static ExtensionManager newInstance(Context context, ExtensionDao extensionDao,
      OkHttpClient httpClient, DataStore<Preferences> dataStore, ExtensionExecutor executor) {
    return new ExtensionManager(context, extensionDao, httpClient, dataStore, executor);
  }
}
