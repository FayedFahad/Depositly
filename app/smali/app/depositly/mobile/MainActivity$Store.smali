.class public Lapp/depositly/mobile/MainActivity$Store;
.super Ljava/lang/Object;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lapp/depositly/mobile/MainActivity;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x1
    name = "Store"
.end annotation


# instance fields
.field final synthetic this$0:Lapp/depositly/mobile/MainActivity;


# direct methods
.method public constructor <init>(Lapp/depositly/mobile/MainActivity;)V
    .locals 0

    .line 37
    iput-object p1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public backup(Ljava/lang/String;)V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 40
    const-string v0, "depositly-entries.json"

    const-string v1, "application/json"

    invoke-virtual {p0, p1, v0, v1}, Lapp/depositly/mobile/MainActivity$Store;->exportFile(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public exportFile(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 41
    :try_start_0
    new-instance v0, Ljava/io/FileOutputStream;

    iget-object v1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-static {v1}, Lapp/depositly/mobile/MainActivity;->access$200(Lapp/depositly/mobile/MainActivity;)Ljava/io/File;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :try_start_1
    sget-object v1, Ljava/nio/charset/StandardCharsets;->UTF_8:Ljava/nio/charset/Charset;

    invoke-virtual {p1, v1}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B

    move-result-object p1

    invoke-virtual {v0, p1}, Ljava/io/OutputStream;->write([B)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    :try_start_2
    invoke-virtual {v0}, Ljava/io/OutputStream;->close()V

    iget-object p1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    new-instance v0, Lapp/depositly/mobile/MainActivity$Store$$ExternalSyntheticLambda1;

    invoke-direct {v0, p0, p3, p2}, Lapp/depositly/mobile/MainActivity$Store$$ExternalSyntheticLambda1;-><init>(Lapp/depositly/mobile/MainActivity$Store;Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {p1, v0}, Lapp/depositly/mobile/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_0

    goto :goto_1

    :catchall_0
    move-exception p1

    :try_start_3
    invoke-virtual {v0}, Ljava/io/OutputStream;->close()V
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    goto :goto_0

    :catchall_1
    move-exception p2

    :try_start_4
    invoke-virtual {p1, p2}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :goto_0
    throw p1
    :try_end_4
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_4} :catch_0

    :catch_0
    move-exception p1

    iget-object p1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    const-string p2, "Could not prepare export. Please try again."

    invoke-static {p1, p2}, Lapp/depositly/mobile/MainActivity;->access$300(Lapp/depositly/mobile/MainActivity;Ljava/lang/String;)V

    :goto_1
    return-void
.end method

.method synthetic lambda$exportFile$0$bd-fdr-vault-MainActivity$Store(Ljava/lang/String;Ljava/lang/String;)V
    .locals 2

    .line 41
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.CREATE_DOCUMENT"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v1, "android.intent.category.OPENABLE"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v0, p1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    const-string p1, "android.intent.extra.TITLE"

    invoke-virtual {v0, p1, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-object p1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    const/16 p2, 0xa

    invoke-virtual {p1, v0, p2}, Lapp/depositly/mobile/MainActivity;->startActivityForResult(Landroid/content/Intent;I)V

    return-void
.end method

.method synthetic lambda$restore$1$bd-fdr-vault-MainActivity$Store()V
    .locals 3

    .line 42
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.OPEN_DOCUMENT"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v1, "android.intent.category.OPENABLE"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    const-string v1, "*/*"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    iget-object v1, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    const/16 v2, 0xb

    invoke-virtual {v1, v0, v2}, Lapp/depositly/mobile/MainActivity;->startActivityForResult(Landroid/content/Intent;I)V

    return-void
.end method

.method public load()Ljava/lang/String;
    .locals 3
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 38
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    const-string v1, "vault"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Lapp/depositly/mobile/MainActivity;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v1, "data"

    const-string v2, ""

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public restore()V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 42
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    new-instance v1, Lapp/depositly/mobile/MainActivity$Store$$ExternalSyntheticLambda0;

    invoke-direct {v1, p0}, Lapp/depositly/mobile/MainActivity$Store$$ExternalSyntheticLambda0;-><init>(Lapp/depositly/mobile/MainActivity$Store;)V

    invoke-virtual {v0, v1}, Lapp/depositly/mobile/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method public save(Ljava/lang/String;)Z
    .locals 3
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 39
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;

    const-string v1, "vault"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Lapp/depositly/mobile/MainActivity;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "data"

    invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->commit()Z

    move-result p1

    return p1
.end method

.method public setTheme(Z)V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    # JavaScript interfaces run off the UI thread; marshal Window changes safely.
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity$Store;->this$0:Lapp/depositly/mobile/MainActivity;
    new-instance v1, Lapp/depositly/mobile/ThemeUpdate;
    invoke-direct {v1, v0, p1}, Lapp/depositly/mobile/ThemeUpdate;-><init>(Lapp/depositly/mobile/MainActivity;Z)V
    invoke-virtual {v0, v1}, Lapp/depositly/mobile/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V
    return-void
.end method
