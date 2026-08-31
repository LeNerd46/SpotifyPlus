import React from "react";
import type { AnimatedNodeLike as LegacyAnimatedNodeLike } from "./animated";
import type { NativeAnimatedNodeLike } from "./native-animation/core";
import type { CSSProperties } from "./native-animation/layout";
import type { LayoutAnimation } from "./native-animation/types";
import type {
    NativeComponentRef,
    ScrollToEndOptions,
    ScrollToIndexOptions,
    ScrollToOffsetOptions,
    ScrollToOptions,
} from "./renderer";
import type {
    ExtensionAsset,
    ExtensionFontAsset,
    ExtensionFontFamily,
} from "../core/extension-assets";

export type LayoutSize =
    | number
    | `${number}dp`
    | `${number}px`
    | `${number}sp`
    | `${number}%`
    | "auto"
    | "match_parent"
    | "match"
    | "fill_parent"
    | "fill"
    | "wrap_content"
    | "wrap";
export type SizeValue =
    | number
    | `${number}dp`
    | `${number}px`
    | `${number}sp`
    | `${number}%`
    | "auto";
export type ColorValue = string | number;
export type ViewShadow = {
    shadowColor?: AnimatedStyleValue<ColorValue>;
    shadowOpacity?: AnimatedStyleValue<number>;
    shadowRadius?: AnimatedStyleValue<SizeValue>;
    shadowOffset?: {
        width?: AnimatedStyleValue<SizeValue>;
        height?: AnimatedStyleValue<SizeValue>;
    };
};
export type VisibilityValue = "visible" | "invisible" | "gone";
export type DisplayValue = "flex" | "none";
export type FlexDirectionValue =
    | "row"
    | "column"
    | "row-reverse"
    | "column-reverse";
export type JustifyContentValue =
    | "flex-start"
    | "center"
    | "flex-end"
    | "space-between"
    | "space-around"
    | "space-evenly";
export type AlignItemsValue =
    | "stretch"
    | "flex-start"
    | "center"
    | "flex-end"
    | "baseline"
    | "space-between"
    | "space-around";
export type AlignSelfValue =
    | "auto"
    | "stretch"
    | "flex-start"
    | "center"
    | "flex-end"
    | "baseline";
export type FlexWrapValue = "nowrap" | "wrap" | "wrap-reverse";
export type OverflowValue = "visible" | "hidden" | "scroll";
export type DirectionValue = "inherit" | "ltr" | "rtl";
export type FontWeightValue =
    | "normal"
    | "bold"
    | "100"
    | "200"
    | "300"
    | "400"
    | "500"
    | "600"
    | "700"
    | "800"
    | "900"
    | 100
    | 200
    | 300
    | 400
    | 500
    | 600
    | 700
    | 800
    | 900;
export type FontStyleValue = "normal" | "italic";
export type TextAlignValue = "auto" | "left" | "center" | "right";
export type EllipsizeModeValue = "head" | "middle" | "tail" | "clip";
export type ResizeModeValue = "cover" | "contain" | "stretch" | "center";
export type KeyboardTypeValue =
    | "default"
    | "email-address"
    | "numeric"
    | "decimal-pad"
    | "phone-pad";
export type ReturnKeyTypeValue = "done" | "go" | "next" | "search" | "send";
export type OrientationValue = "horizontal" | "vertical";
export type GravityValue =
    | "center"
    | "center_horizontal"
    | "center_vertical"
    | "start"
    | "end"
    | "left"
    | "right"
    | "top"
    | "bottom"
    | `${string}|${string}`;
export type ShowDividersValue =
    | "none"
    | "beginning"
    | "middle"
    | "end"
    | `${string}|${string}`;
export type OverScrollModeValue = "always" | "ifContentScrolls" | "never";
export type ScaleTypeValue =
    | "centerCrop"
    | "center_crop"
    | "fitCenter"
    | "fit_center"
    | "fitXY"
    | "fit_xy"
    | "center"
    | "centerInside"
    | "center_inside"
    | "fitStart"
    | "fit_start"
    | "fitEnd"
    | "fit_end"
    | "matrix";
export type NativeNodeId = number;
export type ImageSource = string | ExtensionAsset | { uri: string };
export type FontFamilySource = string | ExtensionFontAsset | ExtensionFontFamily;
export type StyleProp<T> =
    | T
    | null
    | undefined
    | false
    | ReadonlyArray<StyleProp<T>>;
export type AnimatedStyleValue<T> = T | LegacyAnimatedNodeLike | NativeAnimatedNodeLike;

type ReactChildren = React.ReactNode;
type HostProps = Record<string, unknown>;

export interface PressEvent {
    targetId: number;
    x: number;
    y: number;
}
export interface FocusEvent {
    targetId: number;
    hasFocus: boolean;
}
export interface ScrollEvent {
    targetId: number;
    x: number;
    y: number;
    oldX: number;
    oldY: number;
}
export interface VisibleRangeEvent {
    targetId: number;
    first: number;
    last: number;
    visibleFirst: number;
    visibleLast: number;
}
export interface ViewToken<ItemT = any> {
    item: ItemT;
    index: number;
    key: string;
    isViewable: boolean;
}
export interface ViewabilityChangeEvent<ItemT = any> {
    viewableItems: ViewToken<ItemT>[];
    changed: ViewToken<ItemT>[];
}
export interface SubmitEditingEvent {
    targetId: number;
    text: string;
    actionId: number;
}
export interface ImageErrorEvent {
    targetId: number;
    src: string;
    error: string;
}

export interface LayoutStyle {
    width?: LayoutSize;
    height?: LayoutSize;
    minWidth?: LayoutSize;
    minHeight?: LayoutSize;
    maxWidth?: LayoutSize;
    maxHeight?: LayoutSize;
    margin?: SizeValue;
    marginHorizontal?: SizeValue;
    marginVertical?: SizeValue;
    marginLeft?: SizeValue;
    marginRight?: SizeValue;
    marginTop?: SizeValue;
    marginBottom?: SizeValue;
    marginStart?: SizeValue;
    marginEnd?: SizeValue;
    padding?: SizeValue;
    paddingHorizontal?: SizeValue;
    paddingVertical?: SizeValue;
    paddingLeft?: SizeValue;
    paddingRight?: SizeValue;
    paddingTop?: SizeValue;
    paddingBottom?: SizeValue;
    paddingStart?: SizeValue;
    paddingEnd?: SizeValue;
    position?: "relative" | "absolute" | "static";
    top?: SizeValue;
    bottom?: SizeValue;
    left?: SizeValue;
    right?: SizeValue;
    start?: SizeValue;
    end?: SizeValue;
    display?: DisplayValue;
    flex?: number;
    flexGrow?: number;
    flexShrink?: number;
    flexBasis?: LayoutSize;
    flexDirection?: FlexDirectionValue;
    justifyContent?: JustifyContentValue;
    alignItems?: AlignItemsValue;
    alignSelf?: AlignSelfValue;
    flexWrap?: FlexWrapValue;
    overflow?: OverflowValue;
    direction?: DirectionValue;
    aspectRatio?: number;
    gap?: SizeValue;
    rowGap?: SizeValue;
    columnGap?: SizeValue;
}

export interface TransformStyle {
    opacity?: AnimatedStyleValue<number>;
    backgroundColor?: ColorValue;
    borderRadius?: SizeValue;
    borderTopLeftRadius?: SizeValue;
    borderTopRightRadius?: SizeValue;
    borderBottomLeftRadius?: SizeValue;
    borderBottomRightRadius?: SizeValue;
    borderWidth?: SizeValue;
    borderLeftWidth?: SizeValue;
    borderTopWidth?: SizeValue;
    borderRightWidth?: SizeValue;
    borderBottomWidth?: SizeValue;
    borderStartWidth?: SizeValue;
    borderEndWidth?: SizeValue;
    borderColor?: ColorValue;
    transform?: ReadonlyArray<{
        translateX?: AnimatedStyleValue<SizeValue>;
        translateY?: AnimatedStyleValue<SizeValue>;
        translateZ?: AnimatedStyleValue<SizeValue>;
        scale?: AnimatedStyleValue<number>;
        scaleX?: AnimatedStyleValue<number>;
        scaleY?: AnimatedStyleValue<number>;
        rotate?: AnimatedStyleValue<string | number>;
        rotateX?: AnimatedStyleValue<string | number>;
        rotateY?: AnimatedStyleValue<string | number>;
        rotation?: AnimatedStyleValue<string | number>;
    }>;
    elevation?: AnimatedStyleValue<SizeValue>;
    scaleX?: AnimatedStyleValue<number>;
    scaleY?: AnimatedStyleValue<number>;
    rotation?: AnimatedStyleValue<number>;
    rotationX?: AnimatedStyleValue<number>;
    rotationY?: AnimatedStyleValue<number>;
    translateX?: AnimatedStyleValue<SizeValue>;
    translateY?: AnimatedStyleValue<SizeValue>;
    translateZ?: AnimatedStyleValue<SizeValue>;
    clipToOutline?: boolean;
}

export interface TextStyle extends LayoutStyle, TransformStyle {
    color?: ColorValue;
    fontSize?: number;
    fontWeight?: FontWeightValue;
    fontStyle?: FontStyleValue;
    fontFamily?: FontFamilySource;
    textAlign?: TextAlignValue;
    lineHeight?: number;
    letterSpacing?: number;
    includeFontPadding?: boolean;
    textTransform?: "none" | "uppercase";
    textShadowColor?: ColorValue;
    textShadowOffset?: { width?: SizeValue; height?: SizeValue };
    textShadowRadius?: SizeValue;
}

export interface ViewStyle extends LayoutStyle, TransformStyle {
    shadow?: ViewShadow;
}

export interface RelativeLayoutRuleProps {
    alignParentTop?: boolean;
    alignParentBottom?: boolean;
    alignParentStart?: boolean;
    alignParentEnd?: boolean;
    centerInParent?: boolean;
    centerHorizontal?: boolean;
    centerVertical?: boolean;
    above?: NativeNodeId;
    below?: NativeNodeId;
    toStartOf?: NativeNodeId;
    toEndOf?: NativeNodeId;
    alignStart?: NativeNodeId;
    alignEnd?: NativeNodeId;
    alignTop?: NativeNodeId;
    alignBottom?: NativeNodeId;
}

export interface CommonViewProps extends RelativeLayoutRuleProps {
    children?: ReactChildren;
    style?: StyleProp<ViewStyle | TextStyle>;
    width?: LayoutSize;
    height?: LayoutSize;
    minWidth?: LayoutSize;
    minHeight?: LayoutSize;
    maxWidth?: LayoutSize;
    maxHeight?: LayoutSize;
    visible?: boolean;
    visibility?: VisibilityValue;
    display?: DisplayValue;
    disabled?: boolean;
    sharedTransitionTag?: string;
    sharedTransitionStyle?: LayoutAnimation;
    enabled?: boolean;
    pointerEvents?: "none" | "auto" | "box-none" | "box-only";
    clickable?: boolean;
    longClickable?: boolean;
    focusable?: boolean;
    focusableInTouchMode?: boolean;
    selected?: boolean;
    activated?: boolean;
    duplicateParentStateEnabled?: boolean;
    hapticFeedbackEnabled?: boolean;
    soundEffectsEnabled?: boolean;
    opacity?: AnimatedStyleValue<number>;
    backgroundColor?: ColorValue;
    borderRadius?: SizeValue;
    borderTopLeftRadius?: SizeValue;
    borderTopRightRadius?: SizeValue;
    borderBottomLeftRadius?: SizeValue;
    borderBottomRightRadius?: SizeValue;
    borderWidth?: SizeValue;
    borderLeftWidth?: SizeValue;
    borderTopWidth?: SizeValue;
    borderRightWidth?: SizeValue;
    borderBottomWidth?: SizeValue;
    borderStartWidth?: SizeValue;
    borderEndWidth?: SizeValue;
    borderColor?: ColorValue;
    transform?: TransformStyle["transform"];
    clipToOutline?: boolean;
    elevation?: AnimatedStyleValue<SizeValue>;
    rotation?: AnimatedStyleValue<number>;
    rotationX?: AnimatedStyleValue<number>;
    rotationY?: AnimatedStyleValue<number>;
    scaleX?: AnimatedStyleValue<number>;
    scaleY?: AnimatedStyleValue<number>;
    translateX?: AnimatedStyleValue<SizeValue>;
    translateY?: AnimatedStyleValue<SizeValue>;
    translateZ?: AnimatedStyleValue<SizeValue>;
    shadow?: ViewShadow;
    accessibilityLabel?: string;
    contentDescription?: string;
    testID?: string;
    nativeID?: string;
    tag?: string | number | boolean;
    keepScreenOn?: boolean;
    fitsSystemWindows?: boolean;
    clipChildren?: boolean;
    clipToPadding?: boolean;
    margin?: SizeValue;
    marginHorizontal?: SizeValue;
    marginVertical?: SizeValue;
    marginLeft?: SizeValue;
    marginRight?: SizeValue;
    marginTop?: SizeValue;
    marginBottom?: SizeValue;
    marginStart?: SizeValue;
    marginEnd?: SizeValue;
    padding?: SizeValue;
    paddingHorizontal?: SizeValue;
    paddingVertical?: SizeValue;
    paddingLeft?: SizeValue;
    paddingRight?: SizeValue;
    paddingTop?: SizeValue;
    paddingBottom?: SizeValue;
    paddingStart?: SizeValue;
    paddingEnd?: SizeValue;
    flex?: number;
    flexGrow?: number;
    flexShrink?: number;
    flexBasis?: LayoutSize;
    flexDirection?: FlexDirectionValue;
    justifyContent?: JustifyContentValue;
    alignItems?: AlignItemsValue;
    alignSelf?: AlignSelfValue;
    flexWrap?: FlexWrapValue;
    overflow?: OverflowValue;
    direction?: DirectionValue;
    aspectRatio?: number;
    gap?: SizeValue;
    rowGap?: SizeValue;
    columnGap?: SizeValue;
    position?: "relative" | "absolute" | "static";
    top?: SizeValue;
    bottom?: SizeValue;
    left?: SizeValue;
    right?: SizeValue;
    start?: SizeValue;
    end?: SizeValue;
    layoutWeight?: number;
    orientation?: OrientationValue;
    gravity?: GravityValue;
    layoutGravity?: GravityValue;
    weightSum?: number;
    showDividers?: ShowDividersValue;
    dividerPadding?: SizeValue;
    baselineAligned?: boolean;
    onClick?: (event: PressEvent) => void;
    onLongClick?: (event: PressEvent) => void;
    onPress?: (event: PressEvent) => void;
    onLongPress?: (event: PressEvent) => void;
    onPressIn?: (event: PressEvent) => void;
    onPressOut?: (event: PressEvent) => void;
    onFocus?: (event: FocusEvent) => void;
    onBlur?: (event: FocusEvent) => void;
}

export interface ViewProps extends CommonViewProps { }
export interface FrameLayoutProps extends CommonViewProps { }
export interface RelativeLayoutProps extends CommonViewProps { }
export interface PlainViewProps extends CommonViewProps { }

export interface TextProps extends CommonViewProps {
    style?: StyleProp<TextStyle | ViewStyle>;
    children?: ReactChildren;
    text?: string | number;
    color?: ColorValue;
    fontSize?: number;
    fontWeight?: FontWeightValue;
    fontStyle?: FontStyleValue;
    fontFamily?: FontFamilySource;
    textAlign?: TextAlignValue;
    lineHeight?: number;
    letterSpacing?: number;
    numberOfLines?: number;
    ellipsizeMode?: EllipsizeModeValue;
    selectable?: boolean;
    allowFontPadding?: boolean;
    textTransform?: "none" | "uppercase";
    textShadowColor?: ColorValue;
    textShadowOffset?: { width?: SizeValue; height?: SizeValue };
    textShadowRadius?: SizeValue;
    hint?: string;
    hintColor?: ColorValue;
    textColor?: ColorValue;
    textSizeSp?: number;
    maxLines?: number;
    minLines?: number;
    lines?: number;
    singleLine?: boolean;
    allCaps?: boolean;
    includeFontPadding?: boolean;
    textStyle?: "normal" | "bold" | "italic" | "bold|italic" | "italic|bold";
    ellipsize?: "start" | "middle" | "end" | "marquee";
    textIsSelectable?: boolean;
    lineSpacingExtra?: number;
    lineSpacingMultiplier?: number;
    maxLength?: number;
}

export interface TextInputProps extends TextProps {
    value?: string | number;
    defaultValue?: string | number;
    placeholder?: string;
    placeholderTextColor?: ColorValue;
    keyboardType?: KeyboardTypeValue;
    secureTextEntry?: boolean;
    multiline?: boolean;
    returnKeyType?: ReturnKeyTypeValue;
    selectTextOnFocus?: boolean;
    caretHidden?: boolean;
    editable?: boolean;
    inputType?: string | number;
    imeOptions?: string | number;
    selectAllOnFocus?: boolean;
    cursorVisible?: boolean;
    onChangeText?: (text: string) => void;
    onSubmitEditing?: (event: SubmitEditingEvent) => void;
}

export interface ImageProps extends CommonViewProps {
    style?: StyleProp<ViewStyle>;
    source?: ImageSource;
    src?: ImageSource;
    resizeMode?: ResizeModeValue;
    scaleType?: ScaleTypeValue;
    tintColor?: ColorValue;
    adjustViewBounds?: boolean;
    cropToPadding?: boolean;
    onError?: (event: ImageErrorEvent) => void;
}

export interface ButtonProps extends TextProps {
    title?: string;
}
export interface ProgressBarProps extends CommonViewProps {
    indeterminate?: boolean;
    min?: number;
    progress?: number;
    secondaryProgress?: number;
    max?: number;
    progressTintColor?: ColorValue;
    secondaryProgressTintColor?: ColorValue;
    progressBackgroundTintColor?: ColorValue;
    indeterminateTintColor?: ColorValue;
}
export interface ActivityIndicatorProps extends CommonViewProps {
    animating?: boolean;
    color?: ColorValue;
    hidesWhenStopped?: boolean;
}
export interface SliderProps extends ProgressBarProps {
    thumbTintColor?: ColorValue;
    tickMarkTintColor?: ColorValue;
    splitTrack?: boolean;
    onValueChange?: (value: number) => void;
    onSlidingStart?: (value: number) => void;
    onSlidingComplete?: (value: number) => void;
}
export interface CompoundButtonProps extends TextProps {
    checked?: boolean;
    value?: boolean;
    buttonTintColor?: ColorValue;
    onValueChange?: (value: boolean) => void;
}
export interface SwitchProps extends CompoundButtonProps {
    thumbColor?: ColorValue;
    trackColor?: ColorValue;
    thumbTintColor?: ColorValue;
    trackTintColor?: ColorValue;
    textOn?: string;
    textOff?: string;
    showText?: boolean;
}
export interface ScrollViewProps extends CommonViewProps {
    style?: StyleProp<ViewStyle>;
    horizontal?: boolean;
    contentContainerStyle?: StyleProp<ViewStyle>;
    fillViewport?: boolean;
    smoothScrollingEnabled?: boolean;
    verticalScrollBarEnabled?: boolean;
    horizontalScrollBarEnabled?: boolean;
    showsVerticalScrollIndicator?: boolean;
    showsHorizontalScrollIndicator?: boolean;
    overScrollMode?: OverScrollModeValue;
    onScroll?: (event: ScrollEvent) => void;
}
export interface HorizontalScrollViewProps extends ScrollViewProps { }
export interface CheckBoxProps extends CompoundButtonProps { }
export interface RadioButtonProps extends CompoundButtonProps { }
export interface RadioGroupProps extends CommonViewProps {
    checkedId?: NativeNodeId | null;
    onValueChange?: (checkedId: NativeNodeId | null) => void;
}
export interface ImageButtonProps extends ImageProps { }
export interface ToggleButtonProps extends CompoundButtonProps {
    textOn?: string;
    textOff?: string;
    disabledAlpha?: number;
}
export interface SpaceProps extends CommonViewProps { }

export type ScriptViewLength = SizeValue | `${number}%`;
export type ScriptViewGradient = { type?: "linear" | "linearGradient" | "radial" | "radialGradient" | "sweep" | "sweepGradient"; colors: ColorValue[]; positions?: number[]; startX?: ScriptViewLength; startY?: ScriptViewLength; endX?: ScriptViewLength; endY?: ScriptViewLength; centerX?: ScriptViewLength; centerY?: ScriptViewLength; radius?: ScriptViewLength; };
export type ScriptViewFill = ColorValue | ScriptViewGradient;
export type ScriptViewShadow = { color?: ColorValue; radius?: number; dx?: number; dy?: number; };
export interface ScriptViewBaseNode { id?: string | number; type: string; x?: ScriptViewLength; y?: ScriptViewLength; width?: ScriptViewLength; height?: ScriptViewLength; opacity?: number; alpha?: number; visible?: boolean; rotation?: number | string; scale?: number; scaleX?: number; scaleY?: number; translateX?: ScriptViewLength; translateY?: ScriptViewLength; pivotX?: ScriptViewLength; pivotY?: ScriptViewLength; clip?: boolean; clipRadius?: ScriptViewLength; blendMode?: string; shadow?: ScriptViewShadow; }
export interface ScriptViewGroupNode extends ScriptViewBaseNode { type: "group" | "layer"; children?: ScriptViewNode[]; }
export interface ScriptViewRectNode extends ScriptViewBaseNode { type: "rect" | "roundRect"; fill?: ScriptViewFill; color?: ColorValue; stroke?: ColorValue; strokeColor?: ColorValue; strokeWidth?: ScriptViewLength; radius?: ScriptViewLength; borderRadius?: ScriptViewLength; }
export interface ScriptViewCircleNode extends ScriptViewBaseNode { type: "circle" | "oval"; fill?: ScriptViewFill; color?: ColorValue; stroke?: ColorValue; strokeColor?: ColorValue; strokeWidth?: ScriptViewLength; radius?: ScriptViewLength; cx?: ScriptViewLength; cy?: ScriptViewLength; }
export interface ScriptViewLineNode extends ScriptViewBaseNode { type: "line"; x1?: ScriptViewLength; y1?: ScriptViewLength; x2?: ScriptViewLength; y2?: ScriptViewLength; color?: ColorValue; stroke?: ColorValue; strokeColor?: ColorValue; strokeWidth?: ScriptViewLength; }
export interface ScriptViewPathNode extends ScriptViewBaseNode { type: "path"; commands?: Array<{ cmd: "M" | "L" | "Q" | "C" | "Z" | "moveTo" | "lineTo" | "quadTo" | "cubicTo" | "close"; x?: ScriptViewLength; y?: ScriptViewLength; x1?: ScriptViewLength; y1?: ScriptViewLength; x2?: ScriptViewLength; y2?: ScriptViewLength; }>; fill?: ScriptViewFill; color?: ColorValue; stroke?: ColorValue; strokeColor?: ColorValue; strokeWidth?: ScriptViewLength; }
export interface ScriptViewImageNode extends ScriptViewBaseNode { type: "image"; src?: ImageSource; uri?: ImageSource; resizeMode?: ResizeModeValue; scaleType?: ScaleTypeValue; blurRadius?: number; tintColor?: ColorValue; borderRadius?: ScriptViewLength; }
export interface ScriptViewTextNode extends ScriptViewBaseNode { type: "text"; text: string | number; color?: ColorValue; fill?: ScriptViewFill; fontSize?: number; textSizeSp?: number; fontWeight?: FontWeightValue; fontStyle?: FontStyleValue; fontFamily?: FontFamilySource; textAlign?: "left" | "center" | "right"; lineHeight?: number; maxLines?: number; includeFontPadding?: boolean; }
export type ScriptViewNode = ScriptViewGroupNode | ScriptViewRectNode | ScriptViewCircleNode | ScriptViewLineNode | ScriptViewPathNode | ScriptViewImageNode | ScriptViewTextNode | (ScriptViewBaseNode & Record<string, unknown>);
export interface ScriptViewFrameEvent { targetId: number; time: number; delta: number; width: number; height: number; }
export interface ScriptViewSizeEvent { targetId: number; width: number; height: number; oldWidth: number; oldHeight: number; }
export interface ScriptViewTouchEvent extends PressEvent { pageX: number; pageY: number; action: string; pointerId: number; }
export interface ScriptViewImageEvent { targetId: number; src: string; width?: number; height?: number; error?: string; }
export interface ScriptViewProps extends CommonViewProps { displayList?: ScriptViewNode[]; nodes?: ScriptViewNode[]; autoInvalidate?: boolean; softwareLayer?: boolean; renderEffectBlurRadius?: number; onFrame?: (event: ScriptViewFrameEvent) => void; onSizeChange?: (event: ScriptViewSizeEvent) => void; onTouchStart?: (event: ScriptViewTouchEvent) => void; onTouchMove?: (event: ScriptViewTouchEvent) => void; onTouchEnd?: (event: ScriptViewTouchEvent) => void; onTouchCancel?: (event: ScriptViewTouchEvent) => void; onImageLoad?: (event: ScriptViewImageEvent) => void; onImageError?: (event: ScriptViewImageEvent) => void; onAttached?: (event: { targetId: number }) => void; onDetached?: (event: { targetId: number }) => void; }

export interface PressableStateCallbackType {
    pressed: boolean;
    focused: boolean;
}
export interface PressableProps extends Omit<
    ViewProps,
    "onClick" | "onLongClick" | "style" | "children"
> {
    style?:
    | StyleProp<ViewStyle>
    | ((state: PressableStateCallbackType) => StyleProp<ViewStyle>);
    children?:
    | ReactChildren
    | ((state: PressableStateCallbackType) => ReactChildren);
    onPress?: (event: PressEvent) => void;
    onLongPress?: (event: PressEvent) => void;
    onPressIn?: (event: PressEvent) => void;
    onPressOut?: (event: PressEvent) => void;
}
export interface TouchableOpacityProps extends PressableProps {
    activeOpacity?: number;
}
export interface FlatListScrollToIndexParams {
    index: number;
    animated?: boolean;
    viewOffset?: number;
    viewPosition?: number;
}
export interface FlatListScrollToOffsetParams {
    offset: number;
    animated?: boolean;
}
export interface FlatListGetItemLayoutResult {
    length: number;
    offset: number;
    index: number;
}
export interface FlatListProps<ItemT> extends Omit<
    ScrollViewProps,
    "children"
> {
    data?: readonly ItemT[] | null;
    renderItem: (info: { item: ItemT; index: number }) => React.ReactNode;
    keyExtractor?: (item: ItemT, index: number) => string;
    ListHeaderComponent?: React.ComponentType<any> | React.ReactElement | null;
    ListFooterComponent?: React.ComponentType<any> | React.ReactElement | null;
    ListEmptyComponent?: React.ComponentType<any> | React.ReactElement | null;
    ItemSeparatorComponent?: React.ComponentType<any> | React.ReactElement | null;
    initialNumToRender?: number;
    windowSize?: number;
    maxToRenderPerBatch?: number;
    estimatedItemSize?: number;
    getItemLayout?: (data: readonly ItemT[] | null | undefined, index: number) => FlatListGetItemLayoutResult;
    initialScrollIndex?: number;
    onViewableItemsChanged?: (event: ViewabilityChangeEvent<ItemT>) => void;
    itemLayoutAnimation?: LayoutAnimation;
}

interface VirtualizedListProps extends Omit<ScrollViewProps, "children"> {
    itemCount: number;
    estimatedItemSize?: number;
    initialNumToRender?: number;
    windowSize?: number;
    initialScrollIndex?: number;
    onVisibleRangeChange?: (event: VisibleRangeEvent) => void;
}

interface VirtualizedCellProps extends ViewProps {
    itemIndex: number;
    itemLayoutAnimation?: LayoutAnimation;
}

export interface View extends NativeComponentRef { }
export interface LinearLayout extends View { }
export interface FrameLayout extends View { }
export interface RelativeLayout extends View { }
export interface PlainView extends View { }
export interface Text extends TextView { }
export interface TextView extends View { }
export interface TextInput extends EditText { }
export interface EditText extends TextView { }
export interface Button extends TextView { }
export interface ProgressBar extends View { }
export interface ProgressBarHorizontal extends ProgressBar { }
export interface ActivityIndicator extends ProgressBar { }
export interface Slider extends View { }
export interface SeekBar extends Slider { }
export interface Image extends View { }
export interface ImageView extends Image { }
export interface ImageButton extends Image { }
export interface Switch extends TextView { }
export interface CheckBox extends TextView { }
export interface RadioButton extends TextView { }
export interface RadioGroup extends View { }
export interface ToggleButton extends TextView { }
export interface Space extends View { }
export interface ScriptView extends View { }
export interface RenderView extends ScriptView { }
export interface CanvasView extends ScriptView { }
export interface SafeAreaView extends View { }
export interface ScrollView extends View {
    scrollTo(options?: ScrollToOptions | number, y?: number, animated?: boolean): void;
    scrollToEnd(options?: ScrollToEndOptions): void;
    flashScrollIndicators(): void;
}
export interface HorizontalScrollView extends ScrollView { }
export interface Pressable extends View { }
export interface TouchableOpacity extends Pressable { }
export interface FlatList<ItemT = any> extends ScrollView {
    scrollToIndex(params: FlatListScrollToIndexParams): void;
    scrollToOffset(params: FlatListScrollToOffsetParams): void;
}
export interface HorizontalStackLayout extends View { }
export interface VerticalStackLayout extends View { }
export interface Row extends HorizontalStackLayout { }
export interface Column extends VerticalStackLayout { }

export type RNStyle = ViewStyle | TextStyle | CSSProperties;

function isPlainObject(value: unknown): value is HostProps {
    return !!value && typeof value === "object" && !Array.isArray(value);
}

function flattenStyle(style: StyleProp<RNStyle>): HostProps {
    if (!style) return {};
    if (Array.isArray(style)) {
        const result: HostProps = {};
        for (const entry of style)
            Object.assign(result, flattenStyle(entry as StyleProp<RNStyle>));
        return result;
    }
    return isPlainObject(style) ? { ...style } : {};
}

function normalizeLayoutSize(value: unknown): unknown {
    if (
        value === "match_parent" ||
        value === "match" ||
        value === "fill_parent" ||
        value === "fill"
    )
        return "100%";
    if (value === "wrap_content" || value === "wrap") return "auto";
    return value;
}

function normalizeSizeInput(input: HostProps): HostProps {
    const output: HostProps = { ...input };
    const keys = [
        "width",
        "height",
        "minWidth",
        "minHeight",
        "maxWidth",
        "maxHeight",
        "flexBasis",
        "margin",
        "marginHorizontal",
        "marginVertical",
        "marginLeft",
        "marginRight",
        "marginTop",
        "marginBottom",
        "marginStart",
        "marginEnd",
        "padding",
        "paddingHorizontal",
        "paddingVertical",
        "paddingLeft",
        "paddingRight",
        "paddingTop",
        "paddingBottom",
        "paddingStart",
        "paddingEnd",
        "top",
        "bottom",
        "left",
        "right",
        "start",
        "end",
        "translateX",
        "translateY",
        "translateZ",
        "elevation",
        "borderRadius",
        "borderTopLeftRadius",
        "borderTopRightRadius",
        "borderBottomLeftRadius",
        "borderBottomRightRadius",
        "borderWidth",
        "borderLeftWidth",
        "borderTopWidth",
        "borderRightWidth",
        "borderBottomWidth",
        "borderStartWidth",
        "borderEndWidth",
        "gap",
        "rowGap",
        "columnGap",
        "dividerPadding",
    ];
    for (const key of keys)
        if (output[key] !== undefined)
            output[key] = normalizeLayoutSize(output[key]);
    return output;
}

function mergeTextStyle(
    fontWeight?: FontWeightValue,
    fontStyle?: FontStyleValue,
): HostProps {
    const isBold =
        typeof fontWeight === "number"
            ? fontWeight >= 600
            : fontWeight === "bold" ||
            fontWeight === "600" ||
            fontWeight === "700" ||
            fontWeight === "800" ||
            fontWeight === "900";
    const isItalic = fontStyle === "italic";
    if (isBold && isItalic) return { textStyle: "bold|italic" };
    if (isBold) return { textStyle: "bold" };
    if (isItalic) return { textStyle: "italic" };
    return {};
}

function mapDisplay(
    display?: DisplayValue,
    visible?: boolean,
    explicitVisibility?: VisibilityValue,
): HostProps {
    const output: HostProps = {};
    if (display !== undefined) output.display = display;
    if (explicitVisibility) output.visibility = explicitVisibility;
    else if (visible === false) output.visibility = "gone";
    else if (visible === true) output.visibility = "visible";
    if (display === "none" && output.visibility === undefined)
        output.visibility = "gone";
    return output;
}

function mapDisabled(disabled?: boolean, enabled?: boolean): HostProps {
    if (disabled != null) return { enabled: !disabled };
    if (enabled != null) return { enabled };
    return {};
}

function mapPointerEvents(
    pointerEvents?: "none" | "auto" | "box-none" | "box-only",
    clickable?: boolean,
): HostProps {
    if (pointerEvents === "none" || pointerEvents === "box-none")
        return { clickable: false, longClickable: false, focusable: false };
    if (pointerEvents === "auto" || pointerEvents === "box-only")
        return { clickable: true };
    if (clickable != null) return { clickable };
    return {};
}

function normalizeAngle(value: unknown): unknown {
    if (typeof value !== "string") return value;
    const text = value.trim().toLowerCase();
    if (text.endsWith("deg")) return parseFloat(text);
    if (text.endsWith("rad")) return (parseFloat(text) * 180) / Math.PI;
    return value;
}

function mapTransform(transform: unknown): HostProps {
    const output: HostProps = {};
    if (!Array.isArray(transform)) return output;
    for (const item of transform) {
        if (!isPlainObject(item)) continue;
        for (const [key, value] of Object.entries(item)) {
            if (key === "translateX" && output.translationX === undefined)
                output.translationX = normalizeLayoutSize(value);
            else if (key === "translateY" && output.translationY === undefined)
                output.translationY = normalizeLayoutSize(value);
            else if (key === "translateZ" && output.translationZ === undefined)
                output.translationZ = normalizeLayoutSize(value);
            else if (key === "scale") {
                if (output.scaleX === undefined) output.scaleX = value;
                if (output.scaleY === undefined) output.scaleY = value;
            } else if (key === "scaleX" && output.scaleX === undefined)
                output.scaleX = value;
            else if (key === "scaleY" && output.scaleY === undefined)
                output.scaleY = value;
            else if (
                (key === "rotate" || key === "rotation") &&
                output.rotation === undefined
            )
                output.rotation = normalizeAngle(value);
            else if (key === "rotateX" && output.rotationX === undefined)
                output.rotationX = normalizeAngle(value);
            else if (key === "rotateY" && output.rotationY === undefined)
                output.rotationY = normalizeAngle(value);
        }
    }
    return output;
}

function mapCommonProps(input: HostProps): HostProps {
    const output: HostProps = {
        ...mapDisplay(
            input.display as DisplayValue | undefined,
            input.visible as boolean | undefined,
            input.visibility as VisibilityValue | undefined,
        ),
        ...mapDisabled(
            input.disabled as boolean | undefined,
            input.enabled as boolean | undefined,
        ),
        ...mapPointerEvents(
            input.pointerEvents as
            | "none"
            | "auto"
            | "box-none"
            | "box-only"
            | undefined,
            input.clickable as boolean | undefined,
        ),
        ...mapTransform(input.transform),
    };
    if (input.opacity !== undefined && input.alpha === undefined)
        output.alpha = input.opacity;
    if (input.translateX !== undefined && input.translationX === undefined)
        output.translationX = normalizeLayoutSize(input.translateX);
    if (input.translateY !== undefined && input.translationY === undefined)
        output.translationY = normalizeLayoutSize(input.translateY);
    if (input.translateZ !== undefined && input.translationZ === undefined)
        output.translationZ = normalizeLayoutSize(input.translateZ);
    if (
        input.accessibilityLabel !== undefined &&
        input.contentDescription === undefined
    )
        output.contentDescription = input.accessibilityLabel;
    if (input.testID !== undefined && input.tag === undefined)
        output.tag = input.testID;
    if (input.nativeID !== undefined && input.tag === undefined)
        output.tag = input.nativeID;
    if (
        input.layoutWeight !== undefined &&
        input.flex === undefined &&
        input.flexGrow === undefined
    )
        output.flexGrow = input.layoutWeight;
    if (input.orientation !== undefined && input.flexDirection === undefined)
        output.flexDirection =
            input.orientation === "horizontal" ? "row" : "column";
    return output;
}

function mapTextAlign(textAlign?: TextAlignValue): HostProps {
    if (!textAlign || textAlign === "auto") return {};
    if (textAlign === "center")
        return { gravity: "center_horizontal", textAlignment: "center" };
    if (textAlign === "right")
        return { gravity: "right", textAlignment: "viewEnd" };
    return { gravity: "left", textAlignment: "viewStart" };
}

function mapEllipsizeMode(value?: EllipsizeModeValue): HostProps {
    if (!value || value === "clip") return {};
    if (value === "head") return { ellipsize: "start" };
    if (value === "middle") return { ellipsize: "middle" };
    return { ellipsize: "end" };
}

function mapTextProps(input: HostProps): HostProps {
    const output: HostProps = {
        ...mergeTextStyle(
            input.fontWeight as FontWeightValue | undefined,
            input.fontStyle as FontStyleValue | undefined,
        ),
        ...mapTextAlign(input.textAlign as TextAlignValue | undefined),
        ...mapEllipsizeMode(input.ellipsizeMode as EllipsizeModeValue | undefined),
    };
    if (input.color !== undefined && input.textColor === undefined)
        output.textColor = input.color;
    if (input.fontSize !== undefined && input.textSizeSp === undefined)
        output.textSizeSp = input.fontSize;
    if (input.selectable !== undefined && input.textIsSelectable === undefined)
        output.textIsSelectable = input.selectable;
    if (
        input.allowFontPadding !== undefined &&
        input.includeFontPadding === undefined
    )
        output.includeFontPadding = input.allowFontPadding;
    if (input.numberOfLines !== undefined) {
        output.maxLines = input.numberOfLines;
        if (input.numberOfLines === 1) output.singleLine = true;
    }
    if (input.textTransform === "uppercase" && input.allCaps === undefined)
        output.allCaps = true;
    if (
        input.lineHeight !== undefined &&
        input.fontSize !== undefined &&
        input.lineSpacingExtra === undefined
    ) {
        output.lineSpacingExtra = Math.max(
            0,
            Number(input.lineHeight) - Number(input.fontSize),
        );
        output.lineSpacingMultiplier = 1;
    }
    return output;
}

function mapKeyboardType(
    value?: KeyboardTypeValue,
    secureTextEntry?: boolean,
    multiline?: boolean,
): string | number | undefined {
    if (secureTextEntry) return "password";
    if (multiline) return "multiline";
    if (value === "email-address") return "email";
    if (value === "numeric") return "number";
    if (value === "decimal-pad") return "decimal";
    if (value === "phone-pad") return "phone";
    return "text";
}

function mapReturnKeyType(
    value?: ReturnKeyTypeValue,
): string | number | undefined {
    if (value === "done") return "done";
    if (value === "go") return "go";
    if (value === "next") return "next";
    if (value === "search") return "search";
    if (value === "send") return "send";
    return undefined;
}

function mapTextInputProps(input: HostProps): HostProps {
    const output: HostProps = {};
    if (input.value !== undefined && input.text === undefined)
        output.text = String(input.value);
    else if (input.defaultValue !== undefined && input.text === undefined)
        output.text = String(input.defaultValue);
    if (input.placeholder !== undefined && input.hint === undefined)
        output.hint = input.placeholder;
    if (input.placeholderTextColor !== undefined && input.hintColor === undefined)
        output.hintColor = input.placeholderTextColor;
    if (
        input.selectTextOnFocus !== undefined &&
        input.selectAllOnFocus === undefined
    )
        output.selectAllOnFocus = input.selectTextOnFocus;
    if (input.caretHidden !== undefined && input.cursorVisible === undefined)
        output.cursorVisible = !input.caretHidden;
    if (input.editable !== undefined && input.enabled === undefined)
        output.enabled = input.editable;
    const inputType = mapKeyboardType(
        input.keyboardType as KeyboardTypeValue | undefined,
        input.secureTextEntry as boolean | undefined,
        input.multiline as boolean | undefined,
    );
    if (inputType !== undefined && input.inputType === undefined)
        output.inputType = inputType;
    const imeOptions = mapReturnKeyType(
        input.returnKeyType as ReturnKeyTypeValue | undefined,
    );
    if (imeOptions !== undefined && input.imeOptions === undefined)
        output.imeOptions = imeOptions;
    return output;
}

function mapResizeMode(value?: ResizeModeValue): string | undefined {
    switch (value) {
        case "cover":
            return "centerCrop";
        case "contain":
            return "fitCenter";
        case "stretch":
            return "fitXY";
        case "center":
            return "center";
        default:
            return undefined;
    }
}

function mapButtonProps(input: HostProps): HostProps {
    const output: HostProps = {};
    if (input.title !== undefined && input.text === undefined)
        output.text = input.title;
    return output;
}
function mapCompoundButtonProps(input: HostProps): HostProps {
    const output: HostProps = {};
    if (input.value !== undefined && input.checked === undefined)
        output.checked = input.value;
    return output;
}
function mapSwitchProps(input: HostProps): HostProps {
    const output: HostProps = {};
    if (input.thumbColor !== undefined && input.thumbTintColor === undefined)
        output.thumbTintColor = input.thumbColor;
    if (input.trackColor !== undefined && input.trackTintColor === undefined)
        output.trackTintColor = input.trackColor;
    return output;
}
function mapActivityIndicatorProps(input: HostProps): HostProps {
    const output: HostProps = {};
    if (input.animating !== undefined && input.indeterminate === undefined)
        output.indeterminate = input.animating;
    if (input.color !== undefined && input.progressTintColor === undefined)
        output.progressTintColor = input.color;
    if (input.hidesWhenStopped === true && input.animating === false) {
        output.display = "none";
        output.visibility = "gone";
    }
    return output;
}

function mapImageSource(source: unknown): string | ExtensionAsset | undefined {
    if (typeof source === "string") return source;
    if (
        source &&
        typeof source === "object" &&
        (source as { type?: unknown }).type === "extension-asset"
    ) return source as ExtensionAsset;
    if (
        source &&
        typeof source === "object" &&
        typeof (source as { uri?: unknown }).uri === "string"
    )
        return (source as { uri: string }).uri;
    return undefined;
}

function mapImageProps(input: HostProps): HostProps {
    const output: HostProps = {};
    const src = mapImageSource(input.src) ?? mapImageSource(input.source);
    if (src !== undefined) output.src = src;
    const scaleType = mapResizeMode(
        input.resizeMode as ResizeModeValue | undefined,
    );
    if (scaleType !== undefined && input.scaleType === undefined)
        output.scaleType = scaleType;
    if (input.tintColor !== undefined) output.tintColor = input.tintColor;
    if (input.adjustViewBounds !== undefined)
        output.adjustViewBounds = input.adjustViewBounds;
    if (input.cropToPadding !== undefined)
        output.cropToPadding = input.cropToPadding;
    return output;
}

function cleanUndefined(object: HostProps) {
    for (const key of Object.keys(object))
        if (object[key] === undefined) delete object[key];
}

function normalizeProps<T extends { style?: StyleProp<RNStyle>; children?: React.ReactNode },>(props: T | null | undefined, mapper?: (input: HostProps) => HostProps,): HostProps {
    if (!props) return {};
    const { style, children, ref, ...rest } = props as T & {
        style?: StyleProp<RNStyle>;
        children?: React.ReactNode;
        ref?: React.Ref<unknown>;
    };
    const merged: HostProps = normalizeSizeInput({
        ...flattenStyle(style),
        ...rest,
    });
    const normalized: HostProps = {
        ...merged,
        ...mapCommonProps(merged),
        ...(mapper ? mapper(merged) : {}),
    };
    cleanUndefined(normalized);
    delete normalized.visible;
    delete normalized.disabled;
    delete normalized.pointerEvents;
    delete normalized.opacity;
    delete normalized.translateX;
    delete normalized.translateY;
    delete normalized.translateZ;
    delete normalized.accessibilityLabel;
    delete normalized.testID;
    delete normalized.nativeID;
    delete normalized.layoutWeight;
    delete normalized.orientation;
    delete normalized.transform;
    delete normalized.title;
    delete normalized.color;
    delete normalized.fontSize;
    delete normalized.textAlign;
    delete normalized.lineHeight;
    delete normalized.allowFontPadding;
    delete normalized.ellipsizeMode;
    delete normalized.numberOfLines;
    delete normalized.selectable;
    delete normalized.placeholder;
    delete normalized.placeholderTextColor;
    delete normalized.keyboardType;
    delete normalized.secureTextEntry;
    delete normalized.multiline;
    delete normalized.returnKeyType;
    delete normalized.selectTextOnFocus;
    delete normalized.caretHidden;
    delete normalized.editable;
    delete normalized.defaultValue;
    delete normalized.source;
    delete normalized.resizeMode;
    delete normalized.thumbColor;
    delete normalized.trackColor;
    delete normalized.animating;
    delete normalized.hidesWhenStopped;
    delete normalized.horizontal;
    delete normalized.contentContainerStyle;
    delete normalized.showsVerticalScrollIndicator;
    delete normalized.showsHorizontalScrollIndicator;
    if (children !== undefined) normalized.children = children;
    return normalized;
}

export type RefableProps<P, R = NativeComponentRef> = P & {
    ref?: React.Ref<R>;
};

export interface SpotifyPlusComponent<P, R = NativeComponentRef> {
    (props: RefableProps<P, R>): React.ReactElement | null;
    displayName?: string;
}

function createMappedRef<R extends NativeComponentRef = NativeComponentRef>(nativeRef: NativeComponentRef | null, mapper?: (input: HostProps) => HostProps): R | null {
    if (!nativeRef) return null;

    return {
        get nodeId() {
            return nativeRef.nodeId;
        },

        get type() {
            return nativeRef.type;
        },

        get mounted() {
            return nativeRef.mounted;
        },

        getNativeNodeId() {
            return nativeRef.getNativeNodeId();
        },

        setNativeProps(props: Record<string, any>) {
            nativeRef.setNativeProps(normalizeProps(props as any, mapper));
        },

        focus() {
            nativeRef.focus();
        },

        blur() {
            nativeRef.blur();
        },

        measure(callback) {
            nativeRef.measure(callback);
        },

        measureInWindow(callback) {
            nativeRef.measureInWindow(callback);
        },

        scrollTo(options?: { x?: number; y?: number; animated?: boolean } | number, y?: number, animated?: boolean) {
            nativeRef.scrollTo(options as any, y, animated);
        },

        scrollToEnd(options?: { animated?: boolean }) {
            nativeRef.scrollToEnd(options);
        },

        flashScrollIndicators() {
            nativeRef.flashScrollIndicators();
        },

        dispatchCommand(command: string, args?: Record<string, any>, callback?: (payload: any) => void) {
            nativeRef.dispatchCommand(command, args, callback);
        },

        command(command: string, args?: Record<string, any>, callback?: (payload: any) => void) {
            nativeRef.command(command, args, callback);
        },
    } as R;
}

export interface NativeViewProps extends CommonViewProps {
    [key: string]: any;
}

export type NativeViewOptions = {
    scriptId?: string;
};

export type NativeComponentProps<TProps extends object = {}> = TProps & CommonViewProps;

export function createNativeComponent<TProps extends object = {}>(name: string, options?: NativeViewOptions): SpotifyPlusComponent<NativeComponentProps<TProps>, View> {
    const type = options?.scriptId ? `native:${options.scriptId}/${name}` : `native:${name}`;
    return createHostComponent<NativeComponentProps<TProps>, View>(type, mapViewLike);
}

export const NativeView = createNativeComponent;

function createHostComponent<P extends { style?: StyleProp<RNStyle>; children?: React.ReactNode }, R extends NativeComponentRef = NativeComponentRef>(
    type: string,
    mapper?: (input: HostProps) => HostProps,
): SpotifyPlusComponent<P, R> {
    const Component = React.forwardRef<R, P>((props, ref) => {
        const nativeRef = React.useRef<NativeComponentRef | null>(null);

        React.useImperativeHandle(ref, () => createMappedRef<R>(nativeRef.current, mapper) as R, [mapper]);

        return React.createElement(type, { ...normalizeProps(props, mapper), ref: nativeRef });
    }) as unknown as SpotifyPlusComponent<P, R>;

    Component.displayName = type;
    return Component;
}

const mapViewLike = (input: HostProps) => input;
const mapTextLike = (input: HostProps) => mapTextProps(input);
const mapTextInputLike = (input: HostProps) => ({
    ...mapTextProps(input),
    ...mapTextInputProps(input),
});
const mapImageLike = (input: HostProps) => mapImageProps(input);
const mapButtonLike = (input: HostProps) => ({
    ...mapTextProps(input),
    ...mapButtonProps(input),
});
const mapCompoundButtonLike = (input: HostProps) => ({
    ...mapTextProps(input),
    ...mapCompoundButtonProps(input),
});
const mapSwitchLike = (input: HostProps) => ({
    ...mapTextProps(input),
    ...mapCompoundButtonProps(input),
    ...mapSwitchProps(input),
});
const mapProgressLike = (input: HostProps) => input;
const mapActivityIndicatorLike = (input: HostProps) =>
    mapActivityIndicatorProps(input);
const mapSliderLike = (input: HostProps) => input;

function resolvePressableStyle(
    style: PressableProps["style"],
    state: PressableStateCallbackType,
): StyleProp<ViewStyle> {
    return typeof style === "function" ? style(state) : style;
}
function resolvePressableChildren(
    children: PressableProps["children"],
    state: PressableStateCallbackType,
): ReactChildren {
    return typeof children === "function" ? children(state) : children;
}
function renderComponentOrElement(
    component: React.ComponentType<any> | React.ReactElement | null | undefined,
    props?: Record<string, unknown>,
): React.ReactNode {
    if (!component) return null;
    if (React.isValidElement(component)) return component;
    return React.createElement(component, props ?? {});
}

const NativeScrollView = createHostComponent<
    Omit<
        ScrollViewProps,
        | "horizontal"
        | "contentContainerStyle"
        | "showsVerticalScrollIndicator"
        | "showsHorizontalScrollIndicator"
    >,
    ScrollView
>("ScrollView", mapViewLike);
const NativeHorizontalScrollView = createHostComponent<
    Omit<
        HorizontalScrollViewProps,
        | "horizontal"
        | "contentContainerStyle"
        | "showsVerticalScrollIndicator"
        | "showsHorizontalScrollIndicator"
    >,
    HorizontalScrollView
>("HorizontalScrollView", mapViewLike);

const NativeVirtualizedList = createHostComponent<VirtualizedListProps, FlatList>(
    "VirtualizedList",
    mapViewLike,
);
const VirtualizedCell = createHostComponent<VirtualizedCellProps, View>(
    "VirtualizedCell",
    mapViewLike,
);

export const View = createHostComponent<ViewProps, View>('View', mapViewLike);
export const LinearLayout = createHostComponent<ViewProps, LinearLayout>(
    "LinearLayout",
    mapViewLike,
);
export const FrameLayout = createHostComponent<FrameLayoutProps, FrameLayout>(
    "FrameLayout",
    mapViewLike,
);
export const RelativeLayout = createHostComponent<RelativeLayoutProps, RelativeLayout>(
    "RelativeLayout",
    mapViewLike,
);
export const PlainView = createHostComponent<PlainViewProps, PlainView>(
    "PlainView",
    mapViewLike,
);
export const Text = createHostComponent<TextProps, Text>('Text', mapTextLike);
export const TextView = createHostComponent<TextProps, TextView>("TextView", mapTextLike);
export const TextInput = createHostComponent<TextInputProps, TextInput>(
    "EditText",
    mapTextInputLike,
);
export const EditText = TextInput as unknown as SpotifyPlusComponent<TextInputProps, EditText>;
export const Button = createHostComponent<ButtonProps, Button>("Button", mapButtonLike);
export const ProgressBar = createHostComponent<ProgressBarProps, ProgressBar>(
    "ProgressBar",
    mapProgressLike,
);
export const ProgressBarHorizontal = createHostComponent<ProgressBarProps, ProgressBarHorizontal>(
    "ProgressBarHorizontal",
    mapProgressLike,
);
export const ActivityIndicator = createHostComponent<ActivityIndicatorProps, ActivityIndicator>(
    "ProgressBar",
    mapActivityIndicatorLike,
);
export const Slider = createHostComponent<SliderProps, Slider>(
    "SeekBar",
    mapSliderLike,
);
export const SeekBar = Slider as unknown as SpotifyPlusComponent<SliderProps, SeekBar>;
export const Image = createHostComponent<ImageProps, Image>("Image", mapImageLike);
export const ImageView = Image as unknown as SpotifyPlusComponent<ImageProps, ImageView>;
export const ImageButton = createHostComponent<ImageButtonProps, ImageButton>(
    "ImageButton",
    mapImageLike,
);
export const Switch = createHostComponent<SwitchProps, Switch>("Switch", mapSwitchLike);
export const CheckBox = createHostComponent<CheckBoxProps, CheckBox>(
    "CheckBox",
    mapCompoundButtonLike,
);
export const RadioButton = createHostComponent<RadioButtonProps, RadioButton>(
    "RadioButton",
    mapCompoundButtonLike,
);
export const RadioGroup = createHostComponent<RadioGroupProps, RadioGroup>(
    "RadioGroup",
    mapViewLike,
);
export const ToggleButton = createHostComponent<ToggleButtonProps, ToggleButton>(
    "ToggleButton",
    mapCompoundButtonLike,
);
export const Space = createHostComponent<SpaceProps, Space>("Space", mapViewLike);
export const ScriptView = createHostComponent<ScriptViewProps, ScriptView>("ScriptView", mapViewLike);
export const RenderView = ScriptView as unknown as SpotifyPlusComponent<ScriptViewProps, RenderView>;
export const CanvasView = ScriptView as unknown as SpotifyPlusComponent<ScriptViewProps, CanvasView>;
export const SafeAreaView = View as unknown as SpotifyPlusComponent<ViewProps, SafeAreaView>;

export const ScrollView = React.forwardRef<ScrollView, ScrollViewProps>((props, ref) => {
    const {
        horizontal,
        contentContainerStyle,
        showsVerticalScrollIndicator,
        showsHorizontalScrollIndicator,
        children,
        ...rest
    } = props;

    const hostProps: any = { ...rest };

    if (showsVerticalScrollIndicator !== undefined && hostProps.verticalScrollBarEnabled === undefined)
        hostProps.verticalScrollBarEnabled = showsVerticalScrollIndicator;

    if (showsHorizontalScrollIndicator !== undefined && hostProps.horizontalScrollBarEnabled === undefined)
        hostProps.horizontalScrollBarEnabled = showsHorizontalScrollIndicator;

    const HostComponent = horizontal ? NativeHorizontalScrollView : NativeScrollView;
    const contentStyle = horizontal ? [{ flexDirection: "row" as const }, contentContainerStyle] : contentContainerStyle;
    const content = contentStyle ? React.createElement(View, { style: contentStyle }, children) : children;

    return React.createElement(HostComponent, { ...hostProps, ref }, content);
}) as unknown as SpotifyPlusComponent<ScrollViewProps, ScrollView>;

ScrollView.displayName = "ScrollView";

export const HorizontalScrollView = React.forwardRef<HorizontalScrollView, HorizontalScrollViewProps>((props, ref) =>
    React.createElement(ScrollView, { ...props, horizontal: true, ref }),
) as SpotifyPlusComponent<HorizontalScrollViewProps, HorizontalScrollView>;

HorizontalScrollView.displayName = "HorizontalScrollView";

export const Pressable = React.forwardRef<Pressable, PressableProps>((props, ref) => {
    const { style, children, onPressIn, onPressOut, onFocus, onBlur, ...rest } = props;
    const [pressed, setPressed] = React.useState(false);
    const [focused, setFocused] = React.useState(false);
    const state: PressableStateCallbackType = { pressed, focused };

    return React.createElement(
        View,
        {
            ...rest,
            ref,
            style: resolvePressableStyle(style, state),
            onPressIn: (event: PressEvent) => {
                setPressed(true);
                onPressIn?.(event);
            },
            onPressOut: (event: PressEvent) => {
                setPressed(false);
                onPressOut?.(event);
            },
            onFocus: (event: FocusEvent) => {
                setFocused(true);
                onFocus?.(event);
            },
            onBlur: (event: FocusEvent) => {
                setFocused(false);
                setPressed(false);
                onBlur?.(event);
            },
        },
        resolvePressableChildren(children, state),
    );
}) as SpotifyPlusComponent<PressableProps, Pressable>;

Pressable.displayName = "Pressable";

export const TouchableOpacity = React.forwardRef<TouchableOpacity, TouchableOpacityProps>((props, ref) => {
    const { activeOpacity = 0.2, style, ...rest } = props;

    return React.createElement(Pressable, {
        ...rest,
        ref,
        style: (state: PressableStateCallbackType) => [
            resolvePressableStyle(style, state),
            state.pressed ? { opacity: activeOpacity } : null,
        ],
    });
}) as SpotifyPlusComponent<TouchableOpacityProps, TouchableOpacity>;

TouchableOpacity.displayName = "TouchableOpacity";

type FlatListRow<ItemT> =
    | { kind: "header"; key: string }
    | { kind: "footer"; key: string }
    | { kind: "empty"; key: string }
    | { kind: "item"; key: string; item: ItemT; dataIndex: number }
    | { kind: "separator"; key: string; leadingItem: ItemT; dataIndex: number };

export interface FlatListComponent {
    <ItemT>(props: RefableProps<FlatListProps<ItemT>, FlatList<ItemT>>): React.ReactElement | null;
    displayName?: string;
}

function clampRange(first: number, last: number, itemCount: number) {
    if (itemCount <= 0) return { first: 0, last: -1 };
    return {
        first: Math.max(0, Math.min(first, itemCount - 1)),
        last: Math.max(0, Math.min(last, itemCount - 1)),
    };
}

function renderFlatListRow<ItemT>(
    row: FlatListRow<ItemT>,
    renderItem: FlatListProps<ItemT>["renderItem"],
    ListHeaderComponent: FlatListProps<ItemT>["ListHeaderComponent"],
    ListFooterComponent: FlatListProps<ItemT>["ListFooterComponent"],
    ListEmptyComponent: FlatListProps<ItemT>["ListEmptyComponent"],
    ItemSeparatorComponent: FlatListProps<ItemT>["ItemSeparatorComponent"],
) {
    switch (row.kind) {
        case "header":
            return renderComponentOrElement(ListHeaderComponent);
        case "footer":
            return renderComponentOrElement(ListFooterComponent);
        case "empty":
            return renderComponentOrElement(ListEmptyComponent);
        case "separator":
            return renderComponentOrElement(ItemSeparatorComponent, {
                leadingItem: row.leadingItem,
                leadingIndex: row.dataIndex,
            });
        case "item":
            return renderItem({ item: row.item, index: row.dataIndex });
    }
}

export const FlatList = React.forwardRef(function FlatListInner<ItemT>(
    props: FlatListProps<ItemT>,
    ref: React.Ref<FlatList<ItemT>>,
) {
    const {
        data,
        renderItem,
        keyExtractor,
        ListHeaderComponent,
        ListFooterComponent,
        ListEmptyComponent,
        ItemSeparatorComponent,
        initialNumToRender = 10,
        windowSize = 5,
        maxToRenderPerBatch,
        estimatedItemSize = 64,
        getItemLayout,
        initialScrollIndex,
        onViewableItemsChanged,
        itemLayoutAnimation,
        ...listProps
    } = props;
    const items = data ?? [];
    const nativeRef = React.useRef<FlatList<ItemT> | null>(null);

    const rows = React.useMemo(() => {
        const output: FlatListRow<ItemT>[] = [];
        if (ListHeaderComponent) output.push({ kind: "header", key: "$header" });
        if (items.length === 0) {
            if (ListEmptyComponent) output.push({ kind: "empty", key: "$empty" });
        } else {
            items.forEach((item, index) => {
                const key = keyExtractor ? keyExtractor(item, index) : String(index);
                output.push({ kind: "item", key, item, dataIndex: index });
                if (ItemSeparatorComponent && index < items.length - 1)
                    output.push({ kind: "separator", key: `${key}:separator`, leadingItem: item, dataIndex: index });
            });
        }
        if (ListFooterComponent) output.push({ kind: "footer", key: "$footer" });
        return output;
    }, [ItemSeparatorComponent, ListEmptyComponent, ListFooterComponent, ListHeaderComponent, items, keyExtractor]);

    const dataIndexToRowIndex = React.useMemo(() => {
        const map = new Map<number, number>();
        rows.forEach((row, index) => {
            if (row.kind === "item") map.set(row.dataIndex, index);
        });
        return map;
    }, [rows]);

    const initialRange = React.useMemo(() => {
        const targetRow = initialScrollIndex != null ? dataIndexToRowIndex.get(initialScrollIndex) ?? 0 : 0;
        return clampRange(targetRow, targetRow + Math.max(1, initialNumToRender) - 1, rows.length);
    }, [dataIndexToRowIndex, initialNumToRender, initialScrollIndex, rows.length]);

    const [visibleRange, setVisibleRange] = React.useState(initialRange);

    React.useEffect(() => {
        setVisibleRange(initialRange);
    }, [initialRange.first, initialRange.last]);

    React.useImperativeHandle(ref, () => {
        const base = (createMappedRef<FlatList<ItemT>>(nativeRef.current, mapViewLike) ?? {}) as FlatList<ItemT>;
        return {
            ...base,
            scrollToIndex(params: FlatListScrollToIndexParams) {
                const rowIndex = dataIndexToRowIndex.get(params.index);
                if (rowIndex == null) return;
                const layout = getItemLayout?.(data, params.index);
                nativeRef.current?.dispatchCommand("scrollToIndex", {
                    index: rowIndex,
                    animated: params.animated !== false,
                    viewOffset: params.viewOffset ?? 0,
                    viewPosition: params.viewPosition ?? 0,
                    offset: layout?.offset,
                });
            },
            scrollToOffset(params: FlatListScrollToOffsetParams) {
                nativeRef.current?.dispatchCommand("scrollToOffset", {
                    offset: params.offset,
                    animated: params.animated !== false,
                });
            },
        };
    }, [data, dataIndexToRowIndex, getItemLayout]);

    const renderedRows = rows.slice(visibleRange.first, visibleRange.last + 1);

    return React.createElement(
        NativeVirtualizedList,
        {
            ...listProps,
            ref: nativeRef,
            itemCount: rows.length,
            estimatedItemSize,
            initialNumToRender,
            windowSize,
            initialScrollIndex: initialScrollIndex != null ? dataIndexToRowIndex.get(initialScrollIndex) ?? 0 : undefined,
            onVisibleRangeChange: (event: VisibleRangeEvent) => {
                const next = clampRange(event.first, event.last, rows.length);
                setVisibleRange(next);
                if (onViewableItemsChanged) {
                    const viewableItems = rows
                        .slice(Math.max(0, event.visibleFirst), Math.min(rows.length, event.visibleLast + 1))
                        .filter((row): row is Extract<FlatListRow<ItemT>, { kind: "item" }> => row.kind === "item")
                        .map(row => ({ item: row.item, index: row.dataIndex, key: row.key, isViewable: true }));
                    onViewableItemsChanged({ viewableItems, changed: viewableItems });
                }
            },
        },
        renderedRows.map((row, offset) =>
            React.createElement(
                VirtualizedCell,
                {
                    key: row.key,
                    itemIndex: visibleRange.first + offset,
                    itemLayoutAnimation,
                    style: { width: "100%" },
                },
                renderFlatListRow(row, renderItem, ListHeaderComponent, ListFooterComponent, ListEmptyComponent, ItemSeparatorComponent),
            ),
        ),
    );
}) as FlatListComponent;

FlatList.displayName = "FlatList";

export const HorizontalStackLayout = React.forwardRef<HorizontalStackLayout, ViewProps>((props, ref) =>
    React.createElement(View, { ...props, ref, style: [{ flexDirection: "row" }, props.style] }),
) as SpotifyPlusComponent<ViewProps, HorizontalStackLayout>;

HorizontalStackLayout.displayName = "HorizontalStackLayout";

export const VerticalStackLayout = React.forwardRef<VerticalStackLayout, ViewProps>((props, ref) =>
    React.createElement(View, { ...props, ref, style: [{ flexDirection: "column" }, props.style] }),
) as SpotifyPlusComponent<ViewProps, VerticalStackLayout>;

VerticalStackLayout.displayName = "VerticalStackLayout";

export const Row = HorizontalStackLayout as unknown as SpotifyPlusComponent<ViewProps, Row>;
export const Column = VerticalStackLayout as unknown as SpotifyPlusComponent<ViewProps, Column>;

export const StyleSheet = {
    create<T extends Record<string, RNStyle>>(styles: T): T {
        return styles;
    },
    flatten(style: StyleProp<RNStyle>): HostProps {
        return flattenStyle(style);
    },
    absoluteFillObject: {
        position: "absolute" as const,
        top: 0,
        right: 0,
        bottom: 0,
        left: 0,
    },
    absoluteFill: {
        position: "absolute" as const,
        top: 0,
        right: 0,
        bottom: 0,
        left: 0,
    },
};

export default {
    View,
    LinearLayout,
    FrameLayout,
    RelativeLayout,
    ScrollView,
    HorizontalScrollView,
    PlainView,
    Text,
    TextView,
    TextInput,
    EditText,
    Image,
    ImageView,
    ImageButton,
    Button,
    ProgressBar,
    ProgressBarHorizontal,
    ActivityIndicator,
    Slider,
    SeekBar,
    Switch,
    CheckBox,
    RadioButton,
    RadioGroup,
    ToggleButton,
    Space,
    ScriptView,
    RenderView,
    CanvasView,
    SafeAreaView,
    Pressable,
    TouchableOpacity,
    FlatList,
    HorizontalStackLayout,
    VerticalStackLayout,
    Row,
    Column,
    StyleSheet,
};
