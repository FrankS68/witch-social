package de.witchcafe.social.ui;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.social.AccountService;
import de.witchcafe.social.ImageUpload;
import de.witchcafe.social.PostService;
import de.witchcafe.social.client.SocialNetworks;
import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.domain.PostStatus;
import de.witchcafe.social.domain.SocialPost;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

/**
 * Beitrag schreiben und an ausgewählte Konten senden - sofort oder geplant.
 */
@Route("beitrag")
@PageTitle("Neuer Beitrag")
@Menu(order = 1, icon = "vaadin:edit", title = "Neuer Beitrag")
@PermitAll
public class ComposeView extends Main {

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final PostService postService;
    private final ZoneId zone;

    private final TextArea text = new TextArea("Text");
    private final TextField link = new TextField("Link (optional)");
    private final CheckboxGroup<ConnectedAccount> targets = new CheckboxGroup<>("Veröffentlichen auf");
    private final DateTimePicker scheduleAt = new DateTimePicker("Später veröffentlichen (optional)");
    private final Div preview = new Div();
    private final UnorderedList problems = new UnorderedList();
    private final Span counter = new Span();
    private final Button submit = new Button();
    private @Nullable ImageUpload image;

    public ComposeView(PostService postService, AccountService accountService, SocialNetworks networks,
            CurrentUser currentUser) {
        this.postService = postService;
        this.zone = currentUser.require().getZoneId();
        addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.MaxWidth.SCREEN_MEDIUM);
        add(Ui.title("Neuer Beitrag"));

        List<ConnectedAccount> accounts = accountService.listMineUsable();
        if (accounts.isEmpty()) {
            add(new Paragraph(new Text("Noch keine Konten verbunden - zuerst unter "),
                    new RouterLink("Konten", AccountsView.class),
                    new Text(" ein Netzwerk verbinden.")));
            return;
        }

        text.setWidthFull();
        text.setMinHeight("10em");
        text.setValueChangeMode(ValueChangeMode.LAZY);
        text.addValueChangeListener(e -> refresh());
        counter.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        link.setWidthFull();
        link.setPlaceholder("https://...");
        link.setValueChangeMode(ValueChangeMode.LAZY);
        link.addValueChangeListener(e -> refresh());

        targets.setItems(accounts);
        targets.setItemLabelGenerator(networks::label);
        targets.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        targets.setValue(Set.copyOf(accounts));
        targets.addValueChangeListener(e -> refresh());

        // der Upload-Callback läuft ohne Session-Lock, daher UI-Änderungen über ui.access
        UI ui = UI.getCurrent();
        var upload = new Upload(UploadHandler.inMemory((metadata, data) -> ui.access(() -> {
            image = new ImageUpload(metadata.fileName(), metadata.contentType(), data);
            showPreview(metadata.contentType(), data);
            refresh();
        })));
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/gif", "image/webp");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(MAX_IMAGE_BYTES);
        upload.addFileRemovedListener(e -> {
            image = null;
            preview.removeAll();
            refresh();
        });
        var imageHint = new Span("Bild: max. 8 MB. Instagram nur JPEG, Bluesky max. 976 KB. "
                + "Instagram und Pinterest brauchen ein Bild.");
        imageHint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        scheduleAt.setStep(Duration.ofMinutes(5));
        scheduleAt.setMin(LocalDateTime.now(zone));
        scheduleAt.addValueChangeListener(e -> refresh());

        problems.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontSize.SMALL);

        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickListener(e -> submit());
        var clear = new Button("Zurücksetzen", e -> UI.getCurrent().getPage().reload());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        var actions = new HorizontalLayout(submit, clear);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        add(text, counter, link, upload, imageHint, preview, targets, scheduleAt, problems, actions);
        refresh();
    }

    private void showPreview(String contentType, byte[] data) {
        preview.removeAll();
        var img = new Image("data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(data),
                "Vorschau");
        img.setMaxHeight("200px");
        img.addClassNames(LumoUtility.BorderRadius.SMALL, LumoUtility.Margin.Vertical.SMALL);
        preview.add(img);
    }

    private void refresh() {
        int length = text.getValue().codePointCount(0, text.getValue().length());
        counter.setText(length + " Zeichen");

        List<String> found = postService.validate(text.getValue(), link.getValue(), image, targets.getValue());
        problems.removeAll();
        found.forEach(problem -> problems.add(new ListItem(problem)));

        submit.setText(scheduleAt.getValue() == null ? "Jetzt veröffentlichen" : "Einplanen");
        submit.setIcon(scheduleAt.getValue() == null ? VaadinIcon.PAPERPLANE.create() : VaadinIcon.CLOCK.create());
        submit.setEnabled(found.isEmpty());
    }

    private void submit() {
        LocalDateTime when = scheduleAt.getValue();
        if (when != null && when.isBefore(LocalDateTime.now(zone))) {
            Ui.error("Der Zeitpunkt liegt in der Vergangenheit.");
            return;
        }
        submit.setEnabled(false);
        try {
            SocialPost post = postService.create(text.getValue(), link.getValue(), image, targets.getValue(),
                    when == null ? null : when.atZone(zone).toInstant());
            report(post);
            UI.getCurrent().navigate(PostsView.class);
        } catch (RuntimeException e) {
            Ui.error(e.getMessage() != null ? e.getMessage() : e.toString());
            submit.setEnabled(true);
        }
    }

    private static void report(SocialPost post) {
        if (post.getStatus() == PostStatus.SCHEDULED) {
            Ui.success("Beitrag eingeplant");
        } else if (post.getStatus() == PostStatus.PUBLISHED) {
            Ui.success("Beitrag auf " + post.getTargets().size() + " Konto/Konten veröffentlicht");
        } else {
            Ui.error("Beitrag nicht überall veröffentlicht - Details unter Beiträge");
        }
    }
}
