"""Regression audit against the original DEX files in all supplied Spotify builds.

This checks fingerprint evidence and cardinality, not Xposed or UI execution.
Run from the repository root: python tools/compat/audit.py --decompiles <directory>
"""
import argparse
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET
import traceback
from dex_index import load

VERSIONS = ['9.1.28.2252', '9.1.68.1888', '9.1.76.2032', '9.1.82.2160']
S = 'Ljava/lang/String;'
O = 'Ljava/lang/Object;'
LIST = 'Ljava/util/List;'
SINGLE = 'Lio/reactivex/rxjava3/core/Single;'


class NotApplicable(Exception):
    pass


class Audit:
    def __init__(self, root, version):
        self.version = version
        main = root / ('spotify-' + version.replace('.', '-')) / 'app/src/main'
        self.cs = load(main, Path('build/compat') / (version.split('.')[2] + '.pickle'))
        self.ms = [m for c in self.cs.values() for m in c['methods']]
        self.by_string = defaultdict(set)
        for c in self.cs.values():
            for m in c['methods']:
                for s in m['strings']:
                    self.by_string[s].add(c['name'])
        self.resources = {(x.attrib['type'], x.attrib['name']): int(x.attrib['id'], 16)
                          for x in ET.parse(main / 'res/values/public.xml').getroot()}
        self.rows = []

    def classes(self, *strings):
        sets = [set.union(set(), *(v for s, v in self.by_string.items() if query in s)) for query in strings]
        return [self.cs[n] for n in set.intersection(*sets)]

    def one(self, items):
        items = list(items)
        assert len(items) == 1, f'Expected 1 match; got {len(items)}: {[self.label(x) for x in items[:12]]}'
        return items[0]

    @staticmethod
    def label(x):
        if isinstance(x, dict):
            return x.get('owner', '') + ('#' if 'owner' in x else '') + x['name'] + str(x.get('args', ''))
        return str(x)

    def check(self, name, function):
        try:
            value = function()
            self.rows.append((name, 'PASS', self.label(value)))
        except NotApplicable as reason:
            self.rows.append((name, 'N/A', str(reason)))
        except Exception as error:
            self.rows.append((name, 'FAIL', str(error) + ' at ' + str(traceback.extract_tb(error.__traceback__)[-1].lineno)))

    def resource(self, kind, name):
        return self.resources[kind, name]

    def run(self):
        self.check('Context menu model', self.menu)
        self.check('Context menu item / callback model', self.menu_item)
        self.check('Heart state binder', self.heart)
        self.check('Private-session key and setter', self.private)
        self.check('Private-session state getter', self.private_state)
        self.check('Sleep timer builder and state', self.sleep)
        self.check('Sleep timer row / click / command', self.sleep_row)
        self.check('Swipe interaction payload', self.swipe)
        self.check('Swipe queue entry point', self.queue)
        self.check('Shared Play Next queue dependencies', self.next_up)
        self.check('Drawer array callbacks', self.drawer)
        self.check('Navigation item / set / kind', self.navigation)
        self.check('Encore palette accessor', self.palette)
        self.check('Encore layout theme', lambda: self.one(m for m in self.ms if len(m['args']) == 5 and m['ret'] == 'V' and any('EncoreLayoutTheme: Invalid screen dimensions (' in s for s in m['strings'])))
        self.check('Material ColorScheme', lambda: self.one(self.classes('ColorScheme(primary=', 'surfaceContainerLowest=')))
        self.check('Material theme and palette shape', self.material)
        self.check('Home filter chip renderer', self.chips)
        self.check('Drawer theme', lambda: self.one(self.classes('mainLayoutConfig', 'drawerState', 'No drawer view found with gravity ')))
        self.check('Now Playing gradient', self.gradient)
        self.check('Lyrics player-state wrapper', self.player)
        self.check('Lyrics seek controller', self.seek)
        self.check('Lyrics HTTP header capture', self.http_headers)
        self.check('Lyrics sync playback preferences', self.playback_prefs)
        self.check('Bottom-sheet dialog', self.bottom_sheet)
        self.check('Named player APIs', self.named)
        for layout in ('funkis_home', 'creative_work_header_layout', 'image_header_content', 'entity_title_view', 'connect_device_label'):
            self.check('Theme holder: ' + layout, lambda layout=layout: self.theme_holder(layout))
        self.check('Theme modern album header', self.album_header)
        self.check('Theme row factory', self.row_factory)
        self.check('Theme entity title color resolver', self.title_colors)
        self.check('Theme image-header scrim and gradient', self.image_scrim)
        self.check('Native lyrics line renderer', self.lyrics_line)
        self.check('Now Playing page flag or legacy activity', self.page_flag)
        return self.rows

    def menu(self):
        c = self.one(self.classes('ContextMenuViewModel cannot contain items with duplicate itemResId. id='))
        ctor = self.one(m for m in c['methods'] if m['name'] == '<init>' and len(m['args']) == 3 and m['args'][1:] == (LIST, 'Z'))
        header = self.cs[ctor['args'][0]]
        assert len([f for f in header['fields'] if f[2] == S and not f[3] & 8]) == 2
        return ctor

    def menu_item(self):
        modern = [c for c in self.classes('Exactly one title property must be populated.', 'Exactly one icon property must be populated.')
                  if any(m['name'] == '<init>' and len(m['args']) == 9 and m['args'][0] == S and m['args'][3:7] == ('Ljava/lang/Integer;', S, 'Ljava/lang/Integer;', 'Z') for m in c['methods'])]
        if modern:
            c = self.one(modern)
            fields = [f for f in c['fields'] if not f[3] & 8]
            self.one(m for m in c['methods'] if m['name'] == '<init>' and m['args'] == tuple(f[2] for f in fields))
            action = self.cs[fields[8][2]]
            assert len(action['fields']) == 4 and action['fields'][1][2] == 'I'
            self.one(m for m in action['methods'] if m['name'] == '<init>' and len(m['args']) == 4)
            icon = self.cs[fields[2][2]]
            wrapper = self.one(m for m in icon['methods'] if m['name'] == '<init>' and len(m['args']) == 1)
            assert any(m['args'] in [('Landroid/graphics/drawable/LayerDrawable;',), ('Landroid/graphics/drawable/Drawable;',)] for m in self.cs[wrapper['args'][0]]['methods'])
            return c
        c = self.one(self.classes('audiobook_supplementary_content'))
        self.one(m for m in c['methods'] if m['name'] == '<init>' and m['args'] == ('Landroid/content/Context;', S))
        # In 9.1.68 the accessor is renamed to c; the hook selects it by its model return type.
        self.one(m for m in c['methods'] if not m['args'] and m['ret'] in self.cs and any(f[2] == S for f in self.cs[m['ret']]['fields']))
        return c

    def heart(self):
        c = self.cs['Lcom/spotify/encoreconsumermobile/elements/addtobutton/AddToButtonView;']
        ids = {self.resource('string', 'add_to_button_content_description_add'), self.resource('string', 'add_to_button_content_description_added')}
        m = self.one(m for m in c['methods'] if m['ret'] == 'V' and len(m['args']) == 1 and ids <= m['numbers'])
        state = self.cs[m['args'][0]]
        self.one(f for f in c['fields'] if f[2] == state['name'] and not f[3] & 8)
        binder = self.one(x for x in c['methods'] if x['ret'] == 'V' and x['args'] == (state['name'],)
                          and any(f[0] == c['name'] and f[2] == state['name'] for f in x['fields']))
        assert binder == m
        fields = [f[2] for f in state['fields'] if not f[3] & 8]
        assert fields.count('Z') == 1 and fields.count(S) == 2
        enums = [self.cs[t] for t in fields if t in self.cs and self.cs[t]['parent'] == 'Ljava/lang/Enum;']
        enum = self.one(enums)
        assert any('ADDED' in m['strings'] for m in enum['methods'])
        return m

    def private(self):
        c = self.one(c for c in self.classes('offline_mode', 'play_explicit_content', 'private_session', 'download_over_3g')
                     if not c['interfaces'] and any(m['name'] == '<clinit>' and {'offline_mode', 'play_explicit_content', 'private_session', 'download_over_3g'} <= m['strings'] for m in c['methods']))
        key_types = {f[2] for f in c['fields'] if f[3] & 8}
        return self.one(m for c in self.cs.values() if any(f[2] == 'Lcom/spotify/cosmos/rxrouter/RxRouter;' for f in c['fields']) for m in c['methods']
                        if len(m['args']) == 2 and m['args'][0] in key_types and m['args'][1] == O and m['ret'] == 'Lio/reactivex/rxjava3/core/Completable;')

    def private_state(self):
        name = 'Lcom/spotify/settings/esperanto/proto/SettingsOuterClass$SettingsState;'
        c = self.cs[name]
        self.one(m for m in self.ms if m['name'] == 'onIncognitoModeDisabledByTimer' and m['args'] == () and m['ret'] == 'V' and m['flags'] & 17 == 17)
        return self.one(m for m in c['methods'] if m['ret'] == 'Z' and not m['args'] and (name, 'privateSession_', 'Z') in m['fields'])

    def sleep(self):
        c = self.one(self.classes('createState(Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerMenuElement$Props;'))
        m = self.one(m for m in c['methods'] if m['flags'] & 0x19 == 0x19 and len(m['args']) == 2)
        duration = self.one(set(call for call in m['calls'] if call[3] == 'J' and len(call[2]) == 2 and call[2][0] == 'I'))
        enum = self.cs[duration[2][1]]
        assert enum['parent'] == 'Ljava/lang/Enum;'
        assert any(f[2] == 'Ljava/util/concurrent/TimeUnit;' for f in enum['fields'])
        constructors = [call for call in m['calls'] if call[1] == '<init>' and call[2] == ('J',)]
        assert len(constructors) >= 5 and len(set(constructors)) == 1
        state = self.cs[m['ret']]
        self.one(x for x in state['methods'] if x['name'] == '<init>' and len(x['args']) == len([f for f in state['fields'] if not f[3] & 8]))
        builder = self.one(c for c in self.classes('capacity must be non-negative.') if {'Ljava/util/RandomAccess;', 'Ljava/io/Serializable;'} <= set(c['interfaces']) and {'[Ljava/lang/Object;', 'I', 'Z'} <= {f[2] for f in c['fields']})
        self.one(call for call in m['calls'] if call[2] == () and call[3] == builder['name'])
        self.one(call for call in m['calls'] if call[2] == (LIST,) and call[3] == builder['name'])
        return m

    def sleep_row(self):
        ids = {self.resource('plurals', 'context_menu_sleep_timer_hours'), self.resource('plurals', 'context_menu_sleep_timer_mins')}
        row = self.one(m for m in self.ms if m['args'] == (O, O, O) and m['ret'] == O and ids <= m['numbers'])
        self.one(set(call for call in row['calls'] if len(call[2]) == 4 and call[2][:3] == ('I', 'I', '[Ljava/lang/Object;') and call[3] == S))
        option = self.one(self.classes('createState(Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerDurationOptionElement$Props;'))
        controller = self.one(m for m in option['methods'] if m['name'] == '<init>' and len(m['args']) == 1)['args'][0]
        click = self.one(m for m in self.ms if m['name'] == 'invokeSuspend' and self.resource('string', 'context_menu_sleep_timer_select_message') in m['numbers'] and any(f[0] == option['name'] for f in m['fields']))
        request = self.one(set(call for call in click['calls'] if call[1] == '<init>' and call[2] == ('J',)))
        self.one(set(call for call in click['calls'] if call[1] == '<init>' and len(call[2]) == 9
                     and call[2][1:5] == (S, 'Ljava/lang/Integer;', S, 'Ljava/lang/Integer;') and call[2][8] == 'Z'))
        restriction = self.one(m for m in self.ms if m['ret'] == 'Z' and m['args'] == ()
                               and any(call[0] == 'Lcom/spotify/player/model/Restrictions;' and call[1] == 'disallowSleepTimerDurationReasons' for call in m['calls']))
        if restriction['owner'] != controller:
            self.one(f for f in self.cs[controller]['fields'] if f[2] == restriction['owner'])
        consumer = self.one(m for m in self.ms if m['ret'] == 'V' and m['args'] == (O,)
                            and (any(f[0] == request[0] for f in m['fields']) or any(call[0] == request[0] and call[2] == () and call[3] == 'J' for call in m['calls'])))
        assert any(f[2] in (consumer['owner'], *self.cs[consumer['owner']]['interfaces']) for f in self.cs[controller]['fields'])
        return click

    def swipe(self):
        event = self.one(self.classes('interaction = '))
        fields = [f for f in event['fields'] if not f[3] & 8]
        payload = self.cs[self.one(fields)[2]]
        action = self.one(self.classes('Empty action id'))
        gesture = self.one(self.classes('Empty interaction type'))
        if action != gesture:
            self.one(f for f in payload['fields'] if f[2] == action['name'])
            self.one(f for f in payload['fields'] if f[2] == gesture['name'])
        else:
            assert payload == action
        serializers = self.classes('com.spotify.ubi.model.InteractionId')
        if serializers:
            id_serializer = self.one(serializers)
            id_type = self.one(set(call[0] for m in id_serializer['methods'] if m['name'] == 'deserialize' for call in m['calls'] if call[1] == '<init>' and call[2] == ('I', S)))
            result = self.one(self.classes('com.spotify.ubi.logger.InteractionLoggingResult'))
            result_type = self.one(set(call[0] for m in result['methods'] if m['name'] == 'deserialize' for call in m['calls'] if call[1] == '<init>' and id_type in call[2]))
            self.one(f for f in self.cs[result_type]['fields'] if f[2] == id_type)
            assert any(m['ret'] == result_type and m['args'] and m['args'][0] == event['name'] and not m['flags'] & 1024 for m in self.ms)
        else:
            results = {m['ret'] for m in self.ms if m['args'] == (event['name'],) and not m['flags'] & 1024
                       and m['ret'] in self.cs and 'Ljava/io/Serializable;' in self.cs[m['ret']]['interfaces']
                       and len([f for f in self.cs[m['ret']]['fields'] if not f[3] & 8]) == 2
                       and any(x['name'] == '<init>' and len(x['args']) == 2 for x in self.cs[m['ret']]['methods'])}
            result = self.cs[self.one(results)]
            ctor = self.one(m for m in result['methods'] if m['name'] == '<init>' and len(m['args']) == 2)
            self.one(f for f in result['fields'] if f[2] == ctor['args'][0])
            self.one(f for f in self.cs[ctor['args'][0]]['fields'] if f[2] == S)
        return event

    def queue(self):
        repo = self.one(self.classes('GetQueue', 'AddToQueue', 'SetQueue'))
        enqueue = [m for m in self.ms if m['args'] == ('I', S, S, LIST) and m['ret'] == SINGLE and any(f[2] == repo['name'] for f in self.cs[m['owner']]['fields'])]
        if enqueue:
            return self.one(enqueue)
        assert any({'add_item_to_queue', 'swipe'} <= m['strings'] for m in self.ms)
        self.one(m for m in repo['methods'] if m['args'] == ('Lcom/spotify/player/model/command/SetQueueCommand;',) and m['ret'] == SINGLE)
        self.one(m for m in repo['methods'] if m['args'] == ('Lcom/spotify/player/model/command/AddToQueueCommand;',) and m['ret'] == SINGLE)
        return self.one(m for m in repo['methods'] if m['args'] == ('Lcom/spotify/player/model/ContextTrack;',) and m['ret'] == SINGLE)

    def navigation(self):
        id = self.resource('string', 'navigationbar_musicappitems_create_title')
        item = self.one(set(call for m in self.ms if id in m['numbers'] for call in m['calls'] if call[1] == '<init>'
                            and len(call[2]) == 8 and call[2][1] == 'I' and call[2][5] == 'Ljava/util/Set;' and call[2][7] == 'I'))
        enum = self.cs[item[2][4]]
        assert enum['parent'] == 'Ljava/lang/Enum;'
        assert any({'HOME', 'SEARCH', 'YOUR_LIBRARY', 'CREATE', 'PREMIUM'} <= m['strings'] for m in enum['methods'])
        self.one(f for f in self.cs[item[0]]['fields'] if f[2] == enum['name'] and not f[3] & 8)
        collection = self.one(m for m in self.ms if m['name'] == '<init>' and m['args'] == (item[0],) * 5 + ('I',))
        assert 'Ljava/util/Set;' in self.cs[collection['owner']]['interfaces']
        return collection

    def is_a(self, name, parent):
        if name == parent:
            return True
        c = self.cs.get(name)
        return bool(c and any(self.is_a(t, parent) for t in (c['parent'], *c['interfaces']) if t))

    def next_up(self):
        repo = self.one(self.classes('GetQueue', 'AddToQueue', 'SetQueue'))
        flow = 'Lio/reactivex/rxjava3/core/Flowable;'
        self.one(f for f in repo['fields'] if not f[3] & 8 and self.is_a(f[2], flow))
        reader = self.one(m for m in self.cs[flow]['methods'] if not m['args'] and m['ret'] == SINGLE and m['flags'] & 1)
        command = 'Lcom/spotify/player/model/command/SetQueueCommand;'
        self.one(m for m in self.cs[command]['methods'] if m['flags'] & 8 and m['ret'] == command and m['args'] == (S, LIST, LIST))
        self.one(m for m in repo['methods'] if m['args'] == (command,) and m['ret'] == SINGLE)
        track = self.cs['Lcom/spotify/player/model/ContextTrack;']
        assert {'uri', 'metadata', 'toBuilder'} <= {m['name'] for m in track['methods']}
        builder = self.cs['Lcom/spotify/player/model/ContextTrack$Builder;']
        self.one(m for m in builder['methods'] if m['name'] == 'metadata' and m['args'] == ('Ljava/util/Map;',))
        assert {'nextTracks', 'prevTracks', 'revision'} <= {m['name'] for m in self.cs['Lcom/spotify/player/model/PlayerQueue;']['methods']}
        self.one(m for m in self.cs[SINGLE]['methods'] if m['name'] == 'flatMap' and m['args'] == ('Lio/reactivex/rxjava3/functions/Function;',))
        return reader

    def drawer(self):
        candidates = [c for c in self.cs.values() if c['flags'] & 0x11 == 0x11 and len(c['interfaces']) == 1 and len(c['methods']) == 3 and len(c['fields']) == 4
                      and any(f[2] == '[Ljava/lang/Object;' and f[3] & 1 == 1 for f in c['fields']) and any(f[2] == 'I' and f[3] & 1 == 1 for f in c['fields']) and any(f[2] == 'I' and f[3] & 17 == 17 for f in c['fields'])]
        assert candidates
        for c in candidates:
            self.one(m for m in c['methods'] if m['args'] == (O,) and m['ret'] == O and m['flags'] & 17 == 17)
        return ', '.join(c['name'] for c in candidates)

    def palette(self):
        colors = {4278190080, 4294967295, 4280229663, 4279374354, 4280887593}
        types = set(call[0] for m in self.ms if m['name'] == '<clinit>' and colors <= m['numbers'] for call in m['calls']
                    if call[1] == '<init>' and len(call[2]) == 4 and len(self.cs[call[0]]['fields']) == 4)
        palette = self.one(types)
        return self.one(m for c in self.cs.values() if len(c['fields']) == 1 and len(c['methods']) == 5 for m in c['methods']
                        if m['ret'] == palette and len(m['args']) == 1 and m['flags'] & 9 == 9)

    def chips(self):
        c = self.one(c for c in self.classes('HomeFilterChip') if len(c['fields']) == 4)
        assert len([f for f in c['fields'] if f[2] == 'J' and f[3] & 8]) == 2
        return self.one(m for m in c['methods'] if m['ret'] == 'V' and len(m['args']) == 12 and 'HomeFilterChip' in m['strings'])

    def gradient(self):
        ids = {self.resource('color', 'bg_gradient_start_color'), self.resource('color', 'bg_gradient_end_color')}
        return self.one(m for m in self.ms if m['name'] == '<init>' and m['args'] == ('Landroid/content/Context;', 'Landroid/util/AttributeSet;', 'I') and ids <= m['numbers']
                        and len(self.cs[m['owner']]['fields']) == 1 and self.cs[m['owner']]['fields'][0][2] == 'Landroid/graphics/drawable/GradientDrawable;')

    def player(self):
        candidates = [c for c in self.cs.values() if c['flags'] & 17 == 17 and len(c['interfaces']) == 1
                      and all(any(f[2] == t and f[3] & flags == flags for f in c['fields']) for t, flags in [(S,17),('Ljava/util/ArrayList;',17),(O,1),('Landroid/os/Bundle;',1)])]
        return self.one(m for c in candidates for m in c['methods'] if m['name'] == 'getState')

    def bottom_sheet(self):
        names = {'cancel', 'onAttachedToWindow', 'onCreate', 'onStart', 'setCancelable', 'setCanceledOnTouchOutside', 'setContentView'}
        c = self.one(c for c in self.cs.values() if names <= {m['name'] for m in c['methods']})
        self.one(m for m in c['methods'] if m['ret'] == 'Lcom/google/android/material/bottomsheet/BottomSheetBehavior;')
        return c

    def seek(self):
        rpc = self.one(self.classes('spotify.player.esperanto.proto.ContextPlayer', 'SetOptions'))
        return self.one(c for c in self.cs.values() if len(c['fields']) == 3 and len(c['methods']) == 3 and len(c['interfaces']) == 1
                        and c['flags'] & 17 == 17 and all(any(f[2] == t and f[3] & 17 == 17 for f in c['fields']) for t in (rpc['name'], 'Z')))

    def http_headers(self):
        c = self.one(self.classes('method.isEmpty() == true', ' must not have a request body.', ' must have a request body.'))
        writers = [m for m in c['methods'] if m['args'] == (S, S) and m['ret'] == 'V' and m['flags'] & 17 == 17]
        assert writers
        return ', '.join(self.label(m) for m in writers)

    def playback_prefs(self):
        c = self.one(self.classes('Failed to get preference with key %s'))
        self.one(m for m in c['methods'] if m['args'] == (S,) and m['ret'] == SINGLE)
        write = self.one(m for m in c['methods'] if len(m['args']) == 2 and m['args'][1] == S and m['ret'] == 'Lio/reactivex/rxjava3/core/Completable;')
        value = self.cs[write['args'][0]]
        assert {('valueCase_', 'I'), ('value_', O)} <= {(f[1], f[2]) for f in value['fields']}
        return c

    def named(self):
        for name in ['com/spotify/player/model/AutoValue_PlayerState$Builder', 'com/spotify/player/model/ContextTrack', 'com/spotify/player/model/command/SetQueueCommand', 'com/spotify/music/SpotifyMainActivity']:
            assert 'L'+name+';' in self.cs, name
        return 'Player state, track, queue command and main activity exist'

    def material(self):
        c = self.one(self.classes('ColorScheme(primary=', 'surfaceContainerLowest='))
        constructors = [m for m in c['methods'] if m['name'] == '<init>' and len(m['args']) >= 36 and set(m['args']) == {'J'}]
        assert constructors and max(len(m['args']) for m in constructors) in (36, 48)
        candidates = [m for m in self.ms if m['ret'] == 'V' and m['flags'] & 9 == 9 and len(m['args']) in (6, 7) and m['args'][0] == c['name'] and m['args'][-1] == 'I']
        direct = [m for m in candidates if len(m['args']) == 7 and m['args'][5] != 'I']
        return self.one(direct if direct else candidates)

    def theme_holder(self, layout):
        id = self.resources.get(('layout', layout))
        if id is None:
            raise NotApplicable('Layout absent; corresponding holder not used in this build')
        candidates = [m for m in self.ms if m['name'] == '<init>' and id in m['numbers']]
        if layout == 'connect_device_label':
            candidates = [m for m in candidates if m['args'] == ('Landroid/content/Context;', 'Landroid/util/AttributeSet;', 'I')]
        if layout == 'entity_title_view' and not candidates:
            return self.one(m for m in self.ms if m['flags'] & 9 == 9 and len(m['args']) == 2 and m['args'][0] == 'Landroid/view/LayoutInflater;'
                            and {id, self.resource('id', 'title_text')} <= m['numbers'])
        ctor = self.one(candidates)
        if layout in ('creative_work_header_layout', 'image_header_content'):
            self.one(m for m in self.cs[ctor['owner']]['methods'] if not m['args'] and m['ret'] == 'Landroid/view/View;')
        return ctor

    def album_header(self):
        ids = {self.resource('layout', 'expanded_header'), self.resource('layout', 'condensed_header')}
        ctor = self.one(m for m in self.ms if m['name'] == '<init>' and ids <= m['numbers'])
        self.one(m for m in self.cs[ctor['owner']]['methods'] if not m['args'] and m['ret'] == 'Landroid/view/View;')
        return ctor

    def row_factory(self):
        id = self.resource('layout', 'row_layout')
        return self.one(m for m in self.ms if m['args'] == ('Landroid/view/LayoutInflater;',) and id in m['numbers'])

    def lyrics_line(self):
        line = self.one(m for m in self.ms if m['ret'] == 'V' and m['flags'] & 25 == 25 and {'fontScale', 'lyrics line color'} <= m['strings'])
        builders = [m for m in self.ms if m['ret'] == O and len(m['args']) == 5 and any(call[0] == line['args'][0] and call[1] == '<init>' for call in m['calls'])]
        assert builders
        return line

    def title_colors(self):
        ids = {self.resource('attr', name) for name in ('textBase', 'textSubdued', 'textBrightAccent')}
        calls = {call for m in self.ms if ids <= m['numbers'] for call in m['calls']
                 if call[3] == 'I' and len(call[2]) == 2 and set(call[2]) == {'I', 'Landroid/view/View;'}}
        return self.one(calls)

    def image_scrim(self):
        def shape(call):
            return call[3] == 'V' and len(call[2]) == 7 and call[2][1:3] == ('F', 'F') and call[2][5:] == ('I', 'I')
        def gradient(call):
            return call[1] == '<init>' and LIST in call[2] and call[2].count('J') == 2
        matches = {call for m in self.ms if 'verified_row' in m['strings'] for call in m['calls'] if shape(call)}
        if matches:
            call = self.one(matches)
            scrim = self.one(m for m in self.cs[call[0]]['methods'] if (m['name'], m['args'], m['ret']) == call[1:])
        else:
            scrim = self.one(m for m in self.ms if m['flags'] & 9 == 9 and shape((m['owner'], m['name'], m['args'], m['ret']))
                             and any(gradient(call) for call in m['calls']))
        self.one(set(call for call in scrim['calls'] if gradient(call)))
        return scrim

    def page_flag(self):
        classes = [c for c in self.classes('enable_page_api_npv', 'android-nowplaying-musicinstallation') if any(m['ret'] == 'Z' and not m['args'] for m in c['methods'])]
        if not classes:
            assert 'Lcom/spotify/nowplaying/musicinstallation/NowPlayingActivity;' in self.cs
            return 'Legacy NowPlayingActivity swipe fallback'
        c = self.one(classes)
        return self.one(set(call for m in self.ms if 'now_playing_view_container' in m['strings'] for call in m['calls']
                            if call[0] == c['name'] and not call[2] and call[3] == 'Z'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--decompiles', type=Path, default=Path.home() / 'Documents/spotify-decompiles')
    parser.add_argument('--report', type=Path, default=Path('build/compat/audit.md'))
    args = parser.parse_args()
    reports = {}
    for version in VERSIONS:
        audit = Audit(args.decompiles, version)
        reports[version] = audit.run()
        for name, status, detail in reports[version]:
            print(version, status, name, detail, flush=True)
    lines = ['# Spotify DEX compatibility audit', '', 'Static evidence only; device behavior is not established by this audit.', '', '| Fingerprint | ' + ' | '.join(VERSIONS) + ' |', '|---|' + '---|' * len(VERSIONS)]
    for i, (name, _, _) in enumerate(reports[VERSIONS[0]]):
        lines.append('| '+name+' | '+' | '.join(reports[v][i][1] for v in VERSIONS)+' |')
    for v, rows in reports.items():
        lines.extend(['', '## '+v, ''])
        lines.extend(f'- **{status} {name}:** `{detail}`' for name, status, detail in rows)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    raise SystemExit(any(status == 'FAIL' for rows in reports.values() for _, status, _ in rows))


if __name__ == '__main__':
    main()
