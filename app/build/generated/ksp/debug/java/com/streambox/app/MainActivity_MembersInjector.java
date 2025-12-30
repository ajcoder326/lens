package com.streambox.app;

import com.streambox.app.extension.ExtensionInstaller;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<ExtensionInstaller> extensionInstallerProvider;

  public MainActivity_MembersInjector(Provider<ExtensionInstaller> extensionInstallerProvider) {
    this.extensionInstallerProvider = extensionInstallerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<ExtensionInstaller> extensionInstallerProvider) {
    return new MainActivity_MembersInjector(extensionInstallerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectExtensionInstaller(instance, extensionInstallerProvider.get());
  }

  @InjectedFieldSignature("com.streambox.app.MainActivity.extensionInstaller")
  public static void injectExtensionInstaller(MainActivity instance,
      ExtensionInstaller extensionInstaller) {
    instance.extensionInstaller = extensionInstaller;
  }
}
