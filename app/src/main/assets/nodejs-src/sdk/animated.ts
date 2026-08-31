import * as Components from './components';
import {
    createAnimatedModule,
} from '../ui/animated';

export * from '../ui/animated';

const Animated = createAnimatedModule({
    View: Components.View,
    Text: Components.Text,
    Image: Components.Image,
    ScriptView: Components.ScriptView,
    RenderView: Components.RenderView,
    CanvasView: Components.CanvasView,
    ScrollView: Components.ScrollView,
    FlatList: Components.FlatList,
});

export const View = Animated.View;
export const Text = Animated.Text;
export const Image = Animated.Image;
export const ScriptView = Animated.ScriptView;
export const RenderView = Animated.RenderView;
export const CanvasView = Animated.CanvasView;
export const ScrollView = Animated.ScrollView;
export const FlatList = Animated.FlatList;

export default Animated;
