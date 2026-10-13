.class Lapp/depositly/mobile/MainActivity$2;
.super Landroid/webkit/WebChromeClient;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lapp/depositly/mobile/MainActivity;->onCreate(Landroid/os/Bundle;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lapp/depositly/mobile/MainActivity;


# direct methods
.method constructor <init>(Lapp/depositly/mobile/MainActivity;)V
    .locals 0

    .line 26
    iput-object p1, p0, Lapp/depositly/mobile/MainActivity$2;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-direct {p0}, Landroid/webkit/WebChromeClient;-><init>()V

    return-void
.end method

.method static synthetic lambda$onJsConfirm$0(Landroid/webkit/JsResult;Landroid/content/DialogInterface;I)V
    .locals 0

    .line 27
    invoke-virtual {p0}, Landroid/webkit/JsResult;->confirm()V

    return-void
.end method

.method static synthetic lambda$onJsConfirm$1(Landroid/webkit/JsResult;Landroid/content/DialogInterface;I)V
    .locals 0

    .line 27
    invoke-virtual {p0}, Landroid/webkit/JsResult;->cancel()V

    return-void
.end method

.method static synthetic lambda$onJsConfirm$2(Landroid/webkit/JsResult;Landroid/content/DialogInterface;)V
    .locals 0

    .line 27
    invoke-virtual {p0}, Landroid/webkit/JsResult;->cancel()V

    return-void
.end method


# virtual methods
.method public onJsConfirm(Landroid/webkit/WebView;Ljava/lang/String;Ljava/lang/String;Landroid/webkit/JsResult;)Z
    .locals 0

    .line 27
    new-instance p1, Landroid/app/AlertDialog$Builder;

    iget-object p2, p0, Lapp/depositly/mobile/MainActivity$2;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-direct {p1, p2}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V

    invoke-virtual {p1, p3}, Landroid/app/AlertDialog$Builder;->setMessage(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;

    move-result-object p1

    new-instance p2, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda0;

    invoke-direct {p2, p4}, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda0;-><init>(Landroid/webkit/JsResult;)V

    const-string p3, "Continue"

    invoke-virtual {p1, p3, p2}, Landroid/app/AlertDialog$Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object p1

    new-instance p2, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda1;

    invoke-direct {p2, p4}, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda1;-><init>(Landroid/webkit/JsResult;)V

    const-string p3, "Cancel"

    invoke-virtual {p1, p3, p2}, Landroid/app/AlertDialog$Builder;->setNegativeButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object p1

    new-instance p2, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda2;

    invoke-direct {p2, p4}, Lapp/depositly/mobile/MainActivity$2$$ExternalSyntheticLambda2;-><init>(Landroid/webkit/JsResult;)V

    invoke-virtual {p1, p2}, Landroid/app/AlertDialog$Builder;->setOnCancelListener(Landroid/content/DialogInterface$OnCancelListener;)Landroid/app/AlertDialog$Builder;

    move-result-object p1

    invoke-virtual {p1}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;

    const/4 p1, 0x1

    return p1
.end method
