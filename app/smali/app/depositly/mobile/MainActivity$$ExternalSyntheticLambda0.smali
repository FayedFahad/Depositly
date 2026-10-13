.class public final synthetic Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;
.super Ljava/lang/Object;
.source "D8$$SyntheticClass"

# interfaces
.implements Ljava/lang/Runnable;


# instance fields
.field public final synthetic f$0:Lapp/depositly/mobile/MainActivity;

.field public final synthetic f$1:Ljava/lang/String;


# direct methods
.method public synthetic constructor <init>(Lapp/depositly/mobile/MainActivity;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;->f$0:Lapp/depositly/mobile/MainActivity;

    iput-object p2, p0, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;->f$1:Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public final run()V
    .locals 2

    iget-object v0, p0, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;->f$0:Lapp/depositly/mobile/MainActivity;

    iget-object v1, p0, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;->f$1:Ljava/lang/String;

    invoke-virtual {v0, v1}, Lapp/depositly/mobile/MainActivity;->lambda$message$0$bd-fdr-vault-MainActivity(Ljava/lang/String;)V

    return-void
.end method
