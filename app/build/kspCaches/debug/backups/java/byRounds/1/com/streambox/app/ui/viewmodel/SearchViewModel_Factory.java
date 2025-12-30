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
public final class SearchViewModel_Factory implements Factory<SearchViewModel> {
  private final Provider<ExtensionManager> extensionManagerProvider;

  public SearchViewModel_Factory(Provider<ExtensionManager> extensionManagerProvider) {
    this.extensionManagerProvider = extensionManagerProvider;
  }

  @Override
  public SearchViewModel get() {
    return newInstance(extensionManagerProvider.get());
  }

  public static SearchViewModel_Factory create(
      Provider<ExtensionManager> extensionManagerProvider) {
    return new SearchViewModel_Factory(extensionManagerProvider);
  }

  public static SearchViewModel newInstance(ExtensionManager extensionManager) {
    return new SearchViewModel(extensionManager);
  }
}
