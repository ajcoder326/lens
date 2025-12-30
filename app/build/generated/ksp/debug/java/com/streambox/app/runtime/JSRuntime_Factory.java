package com.streambox.app.runtime;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class JSRuntime_Factory implements Factory<JSRuntime> {
  private final Provider<JSApis> jsApisProvider;

  public JSRuntime_Factory(Provider<JSApis> jsApisProvider) {
    this.jsApisProvider = jsApisProvider;
  }

  @Override
  public JSRuntime get() {
    return newInstance(jsApisProvider.get());
  }

  public static JSRuntime_Factory create(Provider<JSApis> jsApisProvider) {
    return new JSRuntime_Factory(jsApisProvider);
  }

  public static JSRuntime newInstance(JSApis jsApis) {
    return new JSRuntime(jsApis);
  }
}
