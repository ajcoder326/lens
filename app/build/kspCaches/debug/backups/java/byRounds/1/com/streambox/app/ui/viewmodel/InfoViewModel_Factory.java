package com.streambox.app.ui.viewmodel;

import com.streambox.app.extension.ExtensionManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class InfoViewModel_Factory implements Factory<InfoViewModel> {
  private final Provider<ExtensionManager> extensionManagerProvider;

  public InfoViewModel_Factory(Provider<ExtensionManager> extensionManagerProvider) {
    this.extensionManagerProvider = extensionManagerProvider;
  }

  @Override
  public InfoViewModel get() {
    return newInstance(extensionManagerProvider.get());
  }

  public static InfoViewModel_Factory create(Provider<ExtensionManager> extensionManagerProvider) {
    return new InfoViewModel_Factory(extensionManagerProvider);
  }

  public static InfoViewModel newInstance(ExtensionManager extensionManager) {
    return new InfoViewModel(extensionManager);
  }
}
