package lol.yukichat.web;

import java.util.HashMap;
import java.util.Map;

/** The app's own words in Russian and German (English is the key). Store.lang() picks. */
final class L {
    private static final Map<String, String[]> M = new HashMap<>();
    static String lang = "en";

    private static void a(String en, String ru, String de) { M.put(en, new String[]{ru, de}); }

    static {
        a("New tab", "Новая вкладка", "Neuer Tab");
        a("Private tab", "Приватная", "Privater Tab");
        a("Bookmarks", "Закладки", "Lesezeichen");
        a("History", "История", "Verlauf");
        a("Downloads", "Загрузки", "Downloads");
        a("Find in page", "Найти", "Suchen");
        a("Desktop site", "Версия для ПК", "Desktop-Seite");
        a("Settings", "Настройки", "Einstellungen");
        a("Send to PC", "На компьютер", "An PC senden");
        a("Scan QR code", "Сканировать QR-код", "QR-Code scannen");
        a("Open", "Открыть", "Öffnen");
        a("Search with", "Искать в", "Suchen mit");
        a("built in", "встроено", "integriert");
        a("Link copied", "Ссылка скопирована", "Link kopiert");
        a("Added to Home screen", "Добавлено на главный экран", "Zum Startbildschirm hinzugefügt");
        a("Nothing appeared? Allow YukiWeb to create home screen shortcuts in its app settings.", "Ярлык не появился? Разрешите YukiWeb создавать ярлыки на рабочем столе в настройках приложения.", "Nichts erschienen? Erlaube YukiWeb in den App-Einstellungen, Verknüpfungen anzulegen.");
        a("App settings", "Настройки приложения", "App-Einstellungen");
        a("Open a page first", "Сначала откройте страницу", "Öffne zuerst eine Seite");
        a("Sign in first", "Сначала войдите", "Melde dich zuerst an");
        a("Sent to your PC", "Отправлено на компьютер", "An deinen PC gesendet");
        a("Couldn't send", "Не удалось отправить", "Senden fehlgeschlagen");
        a("Tab from your PC", "Вкладка с компьютера", "Tab von deinem PC");
        a("Open", "Открыть", "Öffnen");
        a("Later", "Позже", "Später");
        a("Bookmarked", "Добавлено в закладки", "Lesezeichen gesetzt");
        a("Bookmark removed", "Закладка удалена", "Lesezeichen entfernt");
        a("Copy link", "Копировать ссылку", "Link kopieren");
        a("Save image", "Сохранить изображение", "Bild speichern");
        a("No app can open this link", "Нет приложения для этой ссылки", "Keine App kann diesen Link öffnen");
        a("Share link", "Поделиться ссылкой", "Link teilen");
        a("Signed out", "Вы вышли", "Abgemeldet");
        a("Tabs", "Вкладки", "Tabs");
        a("Private", "Приватная", "Privat");
        a("New group…", "Новая группа…", "Neue Gruppe…");
        a("Group name", "Название группы", "Gruppenname");
        a("Move to", "Переместить в", "Verschieben nach");
        a("Remove from group", "Убрать из группы", "Aus Gruppe entfernen");
        a("Close other tabs", "Закрыть остальные", "Andere Tabs schließen");
        a("Close tab", "Закрыть вкладку", "Tab schließen");
        a("Cancel", "Отмена", "Abbrechen");
        a("All", "Все", "Alle");
        a("Translate", "Перевести", "Übersetzen");
        a("Show original", "Оригинал", "Original");
        a("Install app", "Установить", "App installieren");
        a("Add to Home screen", "На главный экран", "Zum Startbildschirm");
        a("Scan QR", "Сканер QR", "QR scannen");
        a("Translation isn't available", "Перевод недоступен", "Übersetzung nicht verfügbar");
        a("Translating…", "Перевожу…", "Übersetze…");
        a("Couldn't translate this page", "Не удалось перевести страницу", "Seite konnte nicht übersetzt werden");
        a("Your launcher can't add shortcuts", "Лаунчер не поддерживает ярлыки", "Dein Launcher unterstützt keine Verknüpfungen");
        a("Point at a QR code", "Наведите на QR-код", "Auf einen QR-Code richten");
        a("Search", "Найти", "Suchen");
        a("Copy", "Копировать", "Kopieren");
        a("Download complete", "Загрузка завершена", "Download abgeschlossen");
        a("Download failed", "Ошибка загрузки", "Download fehlgeschlagen");
        a("Open in", "Открыть в", "Öffnen in");
        a("Always", "Всегда", "Immer");
        a("app", "приложении", "App");
        a("Links to", "Ссылки на", "Links zu");
        a("will open in the app", "будут открываться в приложении", "öffnen sich in der App");
        a("Open with", "Открыть с помощью", "Öffnen mit");
        a("Sign in with Yuki ID", "Войти через Yuki ID", "Mit Yuki ID anmelden");
        a("Continue as", "Продолжить как", "Weiter als");
        a("Continue", "Продолжить", "Weiter");
        a("Sign-in failed", "Не удалось войти", "Anmeldung fehlgeschlagen");
        a("Use another account", "Другой аккаунт", "Anderes Konto");
        a("Your stuff", "Ваше", "Deine Daten");
        a("Passwords", "Пароли", "Passwörter");
        a("Saved on this phone, encrypted", "Хранятся на телефоне в зашифрованном виде", "Verschlüsselt auf diesem Handy");
        a("Site settings", "Настройки сайтов", "Website-Einstellungen");
        a("Permissions, apps, site data", "Разрешения, приложения, данные", "Berechtigungen, Apps, Daten");
        a("Language", "Язык", "Sprache");
        a("Phone language", "Язык телефона", "Handysprache");
        a("Unlock to see saved passwords", "Разблокируйте, чтобы увидеть пароли", "Entsperren, um Passwörter zu sehen");
        a("When you sign in on a site, YukiWeb offers to save the password here.", "Когда вы входите на сайт, YukiWeb предложит сохранить пароль здесь.", "Wenn du dich anmeldest, bietet YukiWeb an, das Passwort hier zu speichern.");
        a("no username", "без логина", "kein Benutzername");
        a("Copy password", "Копировать пароль", "Passwort kopieren");
        a("Copy username", "Копировать логин", "Benutzername kopieren");
        a("Delete", "Удалить", "Löschen");
        a("Delete this password?", "Удалить этот пароль?", "Dieses Passwort löschen?");
        a("Location", "Геолокация", "Standort");
        a("Notifications", "Уведомления", "Benachrichtigungen");
        a("Cookies across sites", "Межсайтовые cookie", "Websiteübergreifende Cookies");
        a("Loading…", "Загрузка…", "Lädt…");
        a("Sites you give permissions to, or set to open in their app, show up here.", "Здесь появятся сайты с разрешениями или открывающиеся в приложении.", "Websites mit Berechtigungen oder App-Öffnung erscheinen hier.");
        a("Opens in app", "Открывается в приложении", "Öffnet in App");
        a("Allowed", "Разрешено", "Erlaubt");
        a("Blocked", "Запрещено", "Blockiert");
        a("Block", "Запретить", "Blockieren");
        a("Allow", "Разрешить", "Erlauben");
        a("Ask every time", "Спрашивать каждый раз", "Jedes Mal fragen");
        a("Stop opening links in the app", "Не открывать ссылки в приложении", "Links nicht mehr in der App öffnen");
        a("Clear cookies and data for this site", "Очистить cookie и данные сайта", "Cookies und Daten der Website löschen");
        a("Clear cookies and data for", "Очистить cookie и данные", "Cookies und Daten löschen für");
        a("Clear", "Очистить", "Löschen");
        a("Cleared", "Очищено", "Gelöscht");
        a("Save password?", "Сохранить пароль?", "Passwort speichern?");
        a("Update saved password?", "Обновить сохранённый пароль?", "Gespeichertes Passwort aktualisieren?");
        a("Save", "Сохранить", "Speichern");
        a("Update", "Обновить", "Aktualisieren");
        a("Never for this site", "Никогда для сайта", "Nie für diese Website");
        a("Not now", "Не сейчас", "Nicht jetzt");
        a("Sign in as", "Войти как", "Anmelden als");
        a("Search engine", "Поисковая система", "Suchmaschine");
    }

    static String t(String en) {
        String[] v = M.get(en);
        if (v == null || lang.equals("en")) return en;
        return lang.equals("ru") ? v[0] : lang.equals("de") ? v[1] : en;
    }
}
