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
public final class ExtensionsViewModel_Factory implements Factory<ExtensionsViewModel> {
  private final Provider<ExtensionManager> extensionManagerProvider;

  public ExtensionsViewModel_Factory(Provider<ExtensionManager> extensionManagerProvider) {
    this.extensionManagerProvider = extensionManagerProvider;
  }

  @Override
  public ExtensionsViewModel get() {
    return newInstance(extensionManagerProvider.get());
  }

  public static ExtensionsViewModel_Factory create(
      Provider<ExtensionManager> extensionManagerProvider) {
    return new ExtensionsViewModel_Factory(extensionManagerProvider);
  }

  public static ExtensionsViewModel newInstance(ExtensionManager extensionManager) {
    return new ExtensionsViewModel(extensionManager);
  }
}
