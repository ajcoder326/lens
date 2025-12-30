package com.streambox.app.runtime;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class JSApis_Factory implements Factory<JSApis> {
  private final Provider<OkHttpClient> httpClientProvider;

  public JSApis_Factory(Provider<OkHttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public JSApis get() {
    return newInstance(httpClientProvider.get());
  }

  public static JSApis_Factory create(Provider<OkHttpClient> httpClientProvider) {
    return new JSApis_Factory(httpClientProvider);
  }

  public static JSApis newInstance(OkHttpClient httpClient) {
    return new JSApis(httpClient);
  }
}
