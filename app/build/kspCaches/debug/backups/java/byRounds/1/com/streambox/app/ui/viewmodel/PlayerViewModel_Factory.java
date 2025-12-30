package com.streambox.app.ui.viewmodel;

import android.content.Context;
import com.streambox.app.extension.ExtensionManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<ExtensionManager> extensionManagerProvider;

  public PlayerViewModel_Factory(Provider<Context> contextProvider,
      Provider<ExtensionManager> extensionManagerProvider) {
    this.contextProvider = contextProvider;
    this.extensionManagerProvider = extensionManagerProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(contextProvider.get(), extensionManagerProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<Context> contextProvider,
      Provider<ExtensionManager> extensionManagerProvider) {
    return new PlayerViewModel_Factory(contextProvider, extensionManagerProvider);
  }

  public static PlayerViewModel newInstance(Context context, ExtensionManager extensionManager) {
    return new PlayerViewModel(context, extensionManager);
  }
}
