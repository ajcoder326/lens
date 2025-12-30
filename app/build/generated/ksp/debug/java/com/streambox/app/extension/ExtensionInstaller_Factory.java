package com.streambox.app.extension;

import android.content.Context;
import com.streambox.app.data.local.ExtensionDao;
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
public final class ExtensionInstaller_Factory implements Factory<ExtensionInstaller> {
  private final Provider<Context> contextProvider;

  private final Provider<ExtensionDao> extensionDaoProvider;

  public ExtensionInstaller_Factory(Provider<Context> contextProvider,
      Provider<ExtensionDao> extensionDaoProvider) {
    this.contextProvider = contextProvider;
    this.extensionDaoProvider = extensionDaoProvider;
  }

  @Override
  public ExtensionInstaller get() {
    return newInstance(contextProvider.get(), extensionDaoProvider.get());
  }

  public static ExtensionInstaller_Factory create(Provider<Context> contextProvider,
      Provider<ExtensionDao> extensionDaoProvider) {
    return new ExtensionInstaller_Factory(contextProvider, extensionDaoProvider);
  }

  public static ExtensionInstaller newInstance(Context context, ExtensionDao extensionDao) {
    return new ExtensionInstaller(context, extensionDao);
  }
}
