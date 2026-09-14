# Feature Specification: Śledzenie miesięcznego limitu paliwa służbowego

**Feature Branch**: `001-fuel-limit-tracker`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "stworz aplikacje na androida, ktora bedzie pobierac z paragonow ze stacji benzynowej liczbe zatankowanych litrow paliwa i zapisywac je w bazie aplikacji w telefonie. Bedzie mozna latwo sprawdzic ile litrow jeszcze mozna zatankowac korzystajac ze sluzbowej karty. 1. dnia kazdego miesiaca limit bedzie sie resetowal. Aplikacja bedzie miala podstawowe mozliwosci raportowania"

## Clarifications

### Session 2026-09-14

- Q: Czy rozpoznawanie liczby litrów ze zdjęcia paragonu (OCR) ma działać wyłącznie na urządzeniu (offline), czy dopuszczalne jest wysyłanie zdjęcia do zewnętrznej usługi OCR w chmurze? → A: OCR działa wyłącznie na urządzeniu (offline) — żadne zdjęcie nie opuszcza telefonu.
- Q: Czy aplikacja powinna wykrywać i ostrzegać przed prawdopodobnym duplikatem tego samego paragonu, czy odpowiedzialność ma spoczywać wyłącznie na użytkowniku? → A: System ostrzega (nie blokuje) przy zapisie wpisu, gdy wykryje bardzo podobny wpis (ta sama data i liczba litrów) — użytkownik decyduje, czy zapisać mimo to.
- Q: Jeśli użytkownik zmieni wartość miesięcznego limitu w trakcie trwającego miesiąca, czy nowa wartość zastępuje limit natychmiast dla bieżącego miesiąca, czy obowiązuje dopiero od następnego miesiąca? → B: Zmiana limitu obowiązuje dopiero od kolejnego miesiąca kalendarzowego — bieżący miesiąc kończy się na dotychczasowej wartości.
- Q: Czy zdjęcia paragonów mają pozostawać w bazie na stałe, czy aplikacja powinna automatycznie usuwać stare zdjęcia po pewnym czasie, zachowując dane liczbowe wpisu? → B: Aplikacja automatycznie usuwa zdjęcie paragonu po 12 miesiącach od daty tankowania, zachowując resztę danych wpisu (data, litry, źródło wartości).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rejestrowanie tankowania na podstawie zdjęcia paragonu (Priority: P1)

Użytkownik tankuje paliwo służbową kartą, robi zdjęcie paragonu ze stacji benzynowej w aplikacji, a aplikacja automatycznie odczytuje liczbę zatankowanych litrów i zapisuje nowy wpis w lokalnej bazie danych po potwierdzeniu przez użytkownika.

**Why this priority**: To rdzeń całej aplikacji — bez rejestrowania tankowań nie da się wyliczyć zużycia ani pozostałego limitu. Jest to jedyna funkcja, która sama w sobie dostarcza wartość (zamiast zbierać paragony na papierze, użytkownik ma cyfrową historię).

**Independent Test**: Można w pełni przetestować, robiąc zdjęcie przykładowego paragonu, potwierdzając odczytaną liczbę litrów i sprawdzając, że wpis pojawia się w historii tankowań.

**Acceptance Scenarios**:

1. **Given** użytkownik jest na ekranie dodawania tankowania, **When** zrobi czytelne zdjęcie paragonu ze stacji, **Then** aplikacja rozpoznaje liczbę zatankowanych litrów i wyświetla ją do potwierdzenia przed zapisem.
2. **Given** aplikacja rozpoznała liczbę litrów ze zdjęcia, **When** użytkownik zatwierdzi wartość bez zmian, **Then** nowy wpis tankowania (data, litry, zdjęcie paragonu) zostaje zapisany w bazie i widoczny w historii.
3. **Given** zdjęcie paragonu jest nieczytelne lub OCR nie rozpoznał litrów, **When** użytkownik wpisze liczbę litrów ręcznie, **Then** wpis zostaje zapisany z ręcznie podaną wartością i oznaczony jako wprowadzony ręcznie.
4. **Given** użytkownik nie ma dostępu do zdjęcia paragonu, **When** doda tankowanie bez zdjęcia, podając datę i litry ręcznie, **Then** wpis zostaje zapisany bez załączonego zdjęcia.

---

### User Story 2 - Podgląd pozostałego limitu litrów w bieżącym miesiącu (Priority: P1)

Użytkownik otwiera aplikację i natychmiast widzi, ile litrów paliwa może jeszcze zatankować służbową kartą w bieżącym miesiącu, na podstawie przypisanego limitu i sumy dotychczasowych tankowań.

**Why this priority**: To główny powód, dla którego użytkownik sięga po aplikację na co dzień — szybka odpowiedź na pytanie "ile mi jeszcze zostało". Bez tego rejestrowanie tankowań (User Story 1) nie dostarcza pełnej wartości.

**Independent Test**: Można przetestować niezależnie, wprowadzając limit miesięczny i kilka wpisów tankowań z konkretnymi datami/litrami, a następnie sprawdzając, czy wyświetlona pozostała wartość odpowiada wyliczeniu (limit − suma litrów od początku miesiąca).

**Acceptance Scenarios**:

1. **Given** użytkownik ustawił miesięczny limit i zarejestrował część tankowań w bieżącym miesiącu, **When** otworzy ekran główny, **Then** widzi liczbę litrów pozostałych do wykorzystania w tym miesiącu.
2. **Given** suma zatankowanych w miesiącu litrów przekroczyła ustalony limit, **When** użytkownik sprawdzi ekran główny, **Then** aplikacja wyraźnie sygnalizuje przekroczenie limitu (np. wartość ujemna lub odpowiedni komunikat/kolor).
3. **Given** użytkownik jeszcze nie ustawił limitu miesięcznego, **When** otworzy ekran główny, **Then** aplikacja poprosi o skonfigurowanie limitu zamiast pokazywać błędną wartość.

---

### User Story 3 - Automatyczny reset limitu pierwszego dnia miesiąca (Priority: P2)

Z początkiem każdego nowego miesiąca kalendarzowego licznik wykorzystanego limitu zeruje się automatycznie, tak aby użytkownik ponownie dysponował pełnym miesięcznym limitem litrów, a historia wcześniejszych tankowań pozostaje zachowana.

**Why this priority**: Zapewnia poprawność działania funkcji z User Story 2 w dłuższym okresie, ale nie blokuje wartości dostarczanej w pierwszym miesiącu użytkowania, dlatego ma niższy priorytet niż rejestrowanie i podgląd limitu.

**Independent Test**: Można przetestować niezależnie, ustawiając datę systemową na koniec miesiąca z zarejestrowanymi tankowaniami, a następnie przesuwając ją na 1. dzień kolejnego miesiąca i weryfikując, że pozostały limit wraca do pełnej wartości, a wcześniejsze wpisy nadal są widoczne w historii.

**Acceptance Scenarios**:

1. **Given** bieżący miesiąc dobiega końca z częściowo wykorzystanym limitem, **When** nadejdzie 1. dzień kolejnego miesiąca, **Then** pozostały limit dla nowego miesiąca odpowiada pełnemu limitowi miesięcznemu, niepomniejszonemu o tankowania z poprzedniego miesiąca.
2. **Given** reset limitu nastąpił, **When** użytkownik przejrzy historię tankowań, **Then** wpisy z poprzednich miesięcy nadal są widoczne i niezmienione.

---

### User Story 4 - Podstawowe raportowanie zużycia paliwa (Priority: P3)

Użytkownik przegląda historię wszystkich tankowań, podsumowanie zużycia paliwa w wybranym miesiącu oraz porównanie zużycia między miesiącami, aby ocenić swoje wzorce tankowania.

**Why this priority**: Dostarcza dodatkowego wglądu i wartości analitycznej, ale aplikacja pozostaje użyteczna (rejestrowanie tankowań i podgląd limitu) nawet bez rozbudowanego raportowania — stąd niższy priorytet.

**Independent Test**: Można przetestować niezależnie, zasilając bazę danymi z co najmniej dwóch różnych miesięcy i sprawdzając, czy lista historii, podsumowanie miesięczne oraz porównanie miesiąc do miesiąca poprawnie odzwierciedlają te dane.

**Acceptance Scenarios**:

1. **Given** w bazie istnieją zarejestrowane tankowania, **When** użytkownik otworzy historię, **Then** widzi chronologiczną listę wszystkich wpisów z datą i liczbą litrów.
2. **Given** użytkownik wybierze konkretny miesiąc, **When** otworzy podsumowanie, **Then** widzi łączną liczbę zatankowanych litrów, ustalony limit oraz wykorzystanie w tym miesiącu.
3. **Given** dostępne są dane z co najmniej dwóch miesięcy, **When** użytkownik otworzy widok porównania, **Then** widzi zestawienie zużycia litrów pomiędzy wybranymi miesiącami.

---

### Edge Cases

- Co się dzieje, gdy zdjęcie paragonu jest nieczytelne, rozmazane lub OCR nie potrafi rozpoznać liczby litrów?
- Użytkownik próbuje dodać wpis z taką samą datą i liczbą litrów jak istniejący wpis — system wyświetla ostrzeżenie o możliwym duplikacie, ale pozwala zapisać po potwierdzeniu (patrz FR-005a).
- Suma tankowań w miesiącu przekracza limit — aplikacja wyraźnie to sygnalizuje (FR-009), ale nie blokuje możliwości zarejestrowania kolejnego tankowania.
- Użytkownik zmienia wartość limitu miesięcznego w trakcie trwającego miesiąca — nowa wartość obowiązuje dopiero od kolejnego miesiąca, a bieżący miesiąc jest rozliczany według dotychczasowego limitu (patrz FR-006).
- Jak aplikacja klasyfikuje tankowanie zarejestrowane tuż przed lub tuż po północy 1. dnia miesiąca (do którego miesiąca jest zaliczane)?
- Co się dzieje, gdy użytkownik chce edytować lub usunąć błędnie dodany wpis tankowania?
- Co się dzieje, gdy użytkownik nie udzieli aplikacji uprawnień do aparatu lub galerii zdjęć?
- Co się dzieje, gdy w danym miesiącu nie zarejestrowano żadnego tankowania (raport/porównanie dla pustego miesiąca)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUSI umożliwiać użytkownikowi wykonanie zdjęcia paragonu ze stacji benzynowej aparatem telefonu lub wybranie istniejącego zdjęcia z galerii.
- **FR-002**: System MUSI automatycznie rozpoznawać (OCR) liczbę zatankowanych litrów paliwa na podstawie zdjęcia paragonu, przetwarzając zdjęcie wyłącznie lokalnie na urządzeniu, bez wysyłania go do usług zewnętrznych.
- **FR-003**: System MUSI wyświetlić rozpoznaną liczbę litrów użytkownikowi do weryfikacji i umożliwić jej ręczną korektę przed zapisaniem wpisu.
- **FR-004**: System MUSI umożliwiać dodanie wpisu tankowania z ręcznie wprowadzoną liczbą litrów i datą, bez zdjęcia paragonu, gdy zdjęcie nie jest dostępne lub rozpoznawanie się nie powiedzie.
- **FR-005**: System MUSI zapisywać każdy wpis tankowania w lokalnej bazie danych na urządzeniu, zawierający co najmniej: datę, liczbę litrów, opcjonalne zdjęcie paragonu oraz informację, czy wartość pochodzi z automatycznego rozpoznania OCR, z ręcznej korekty wyniku OCR, czy z pełnego ręcznego wpisu.
- **FR-005a**: System MUSI ostrzec użytkownika przed zapisaniem nowego wpisu, jeśli w bazie istnieje już wpis z taką samą datą i taką samą liczbą litrów, pozostawiając użytkownikowi decyzję o zapisaniu mimo ostrzeżenia.
- **FR-006**: System MUSI umożliwiać użytkownikowi ustawienie i późniejszą zmianę miesięcznego limitu litrów przypisanego do służbowej karty paliwowej, przy czym zmiana wprowadzona w trakcie trwającego miesiąca MUSI zacząć obowiązywać dopiero od kolejnego miesiąca kalendarzowego — limit bieżącego miesiąca pozostaje bez zmian.
- **FR-007**: System MUSI wyliczać i wyświetlać liczbę litrów pozostałych do wykorzystania w bieżącym miesiącu jako różnicę między limitem obowiązującym dla bieżącego miesiąca a sumą litrów zatankowanych od początku bieżącego miesiąca kalendarzowego.
- **FR-008**: System MUSI automatycznie resetować wykorzystanie limitu na początku każdego miesiąca kalendarzowego (1. dnia miesiąca), zachowując przy tym pełną historię wcześniejszych tankowań.
- **FR-009**: System MUSI wyraźnie sygnalizować użytkownikowi sytuację, w której suma zatankowanych w danym miesiącu litrów przekracza przyznany limit.
- **FR-010**: System MUSI udostępniać chronologiczną listę historii wszystkich zarejestrowanych tankowań wraz z datą i liczbą litrów każdego wpisu.
- **FR-011**: System MUSI udostępniać podsumowanie zużycia paliwa dla wybranego miesiąca (suma litrów, limit, wykorzystanie).
- **FR-012**: System MUSI umożliwiać porównanie zużycia paliwa pomiędzy różnymi miesiącami.
- **FR-013**: System MUSI umożliwiać edycję oraz usunięcie wcześniej zapisanego wpisu tankowania.
- **FR-014**: System MUSI przechowywać wszystkie dane tankowań i limitu wyłącznie lokalnie w bazie danych na urządzeniu użytkownika.
- **FR-015**: System MUSI automatycznie usuwać zdjęcie paragonu powiązane z wpisem tankowania po upływie 12 miesięcy od daty tankowania, zachowując przy tym wszystkie pozostałe dane wpisu (datę, liczbę litrów, źródło wartości).

### Key Entities *(include if feature involves data)*

- **Wpis tankowania (Fueling Entry)**: Pojedyncze zdarzenie zatankowania paliwa — data i godzina, liczba zatankowanych litrów, opcjonalne zdjęcie paragonu (automatycznie usuwane po 12 miesiącach), źródło wartości litrów (OCR/ręczne), opcjonalna notatka.
- **Limit miesięczny (Monthly Fuel Limit)**: Wartość limitu w litrach przypisana do służbowej karty paliwowej wraz z miesiącem kalendarzowym, od którego obowiązuje. Zmiana wartości limitu tworzy nową wartość obowiązującą od najbliższego kolejnego miesiąca, nie zmieniając limitu już trwającego miesiąca — system przechowuje historię wartości limitu w czasie.
- **Podsumowanie miesiąca (Monthly Summary)**: Agregacja wpisów tankowań dla danego miesiąca — suma zatankowanych litrów, obowiązujący limit, pozostała/przekroczona ilość, wykorzystywana także do porównań między miesiącami.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Użytkownik jest w stanie zarejestrować tankowanie na podstawie zdjęcia paragonu w mniej niż 30 sekund od otwarcia ekranu dodawania wpisu.
- **SC-002**: Użytkownik widzi liczbę litrów pozostałych do wykorzystania w bieżącym miesiącu natychmiast po otwarciu aplikacji, bez dodatkowych kroków nawigacyjnych.
- **SC-003**: Reset wykorzystania limitu następuje automatycznie w 100% przypadków w ciągu pierwszej godziny po rozpoczęciu 1. dnia miesiąca, bez potrzeby ręcznej interwencji użytkownika.
- **SC-004**: Co najmniej 80% wpisów utworzonych na podstawie czytelnego zdjęcia paragonu ma poprawnie rozpoznaną liczbę litrów bez konieczności ręcznej korekty.
- **SC-005**: Użytkownik może przejrzeć pełną historię tankowań oraz porównać zużycie między dwoma wybranymi miesiącami w nie więcej niż 3 dotknięciach ekranu od widoku głównego.
- **SC-006**: 100% wpisów tankowań pozostaje dostępnych w historii po reset limitu miesięcznego (żadne dane nie są tracone przy przejściu między miesiącami).

## Assumptions

- Aplikacja jest przeznaczona do użytku jednoosobowego na jednym urządzeniu — brak wymogu logowania, kont użytkowników ani synchronizacji między urządzeniami w tej wersji.
- Zakłada się jedną służbową kartę paliwową i jeden powiązany z nią limit miesięczny; obsługa wielu kart/limitów jednocześnie jest poza zakresem tej wersji.
- Limit miesięczny jest ustalany ręcznie przez użytkownika w ustawieniach aplikacji i obowiązuje do czasu jego zmiany (nie jest pobierany automatycznie z żadnego systemu zewnętrznego).
- Rozpoznawanie tekstu z paragonu (OCR) nie musi być w 100% skuteczne — dla nieczytelnych zdjęć przewidziana jest ścieżka ręcznego wprowadzenia lub korekty liczby litrów.
- "Litry paliwa" obejmują dowolny rodzaj paliwa (benzyna, diesel, LPG); aplikacja nie rozróżnia typów paliwa w tej wersji.
- Granicą resetu limitu jest początek doby (00:00 czasu lokalnego urządzenia) 1. dnia miesiąca kalendarzowego.
- Przekroczenie limitu jest sygnalizowane informacyjnie, ale nie blokuje możliwości zarejestrowania kolejnego tankowania.
- SC-004 (skuteczność OCR) jest weryfikowane manualnie przez ręczną próbkę wpisów (porównanie liczby wpisów `OCR` wobec `OCR_CORRECTED`), bez dedykowanej telemetrii/analityki w v1.
