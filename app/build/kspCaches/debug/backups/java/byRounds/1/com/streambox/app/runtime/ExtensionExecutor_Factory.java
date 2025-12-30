package com.streambox.app.runtime;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ExtensionExecutor_Factory implements Factory<ExtensionExecutor> {
  private final Provider<Context> contextProvider;

  private final Provider<JSRuntime> jsRuntimeProvider;

  public ExtensionExecutor_Factory(Provider<Context> contextProvider,
      Provider<JSRuntime> jsRuntimeProvider) {
    this.contextProvider = contextProvider;
    this.jsRuntimeProvider = jsRuntimeProvider;
  }

  @Override
  public ExtensionExecutor get() {
    return newInstance(contextProvider.get(), jsRuntimeProvider.get());
  }

  public static ExtensionExecutor_Factory create(Provider<Context> contextProvider,
      Provider<JSRuntime> jsRuntimeProvider) {
    return new ExtensionExecutor_Factory(contextProvider, jsRuntimeProvider);
  }

  public static ExtensionExecutor newInstance(Context context, JSRuntime jsRuntime) {
    return new ExtensionExecutor(context, jsRuntime);
  }
}
