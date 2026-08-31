export type ExtensionSettings = {
    extensionId: string;
    settings: ExtensionSettingSection[];
};

export type BaseExtensionSetting = {
    id: string;
    label: string;
    description?: string;
    disabled?: boolean;
};

export type ExtensionSettingSection = {
    title?: string;
    description?: string;
    items: ExtensionSettingItem[];
};

export type ExtensionToggle = BaseExtensionSetting & {
    type: 'toggle';
    value: boolean;
};

export type ExtensionSlider = BaseExtensionSetting & {
    type: 'slider';
    value: number;
    min: number;
    max: number;
    step?: number;
};

export type ExtensionText = BaseExtensionSetting & {
    type: 'text';
    value: string;
    placeholder?: string;
    maxLength?: number;
};

export type ExtensionNumber = BaseExtensionSetting & {
    type: 'number';
    value: number;
    min?: number;
    max?: number;
    step?: number;
    placeholder?: string;
};

export type ExtensionSelect = BaseExtensionSetting & {
    type: 'select';
    value: string;
    options: ExtensionOption[];
};

export type ExtensionMultiSelect = BaseExtensionSetting & {
    type: 'multi-select';
    value: string[];
    options: ExtensionOption[];
};

export type ExtensionRadio = BaseExtensionSetting & {
    type: 'radio';
    value: string;
    options: ExtensionOption[];
};

export type ExtensionColor = BaseExtensionSetting & {
    type: 'color';
    value: string;
    alpha?: boolean;
};

export type ExtensionButton = BaseExtensionSetting & {
    type: 'button';
    buttonLabel?: string;
};

export type ExtensionDate = BaseExtensionSetting & {
    type: 'date';
    value: string;
    min?: string;
    max?: string;
};

export type ExtensionTime = BaseExtensionSetting & {
    type: 'time';
    value: string;
};

export type ExtensionRange = BaseExtensionSetting & {
    type: 'range';
    value: [number, number];
    min: number;
    max: number;
    step?: number;
};

export type ExtensionFile = BaseExtensionSetting & {
    type: 'file';
    value?: string;
    accept?: string[];
};

export type ExtensionDirectory = BaseExtensionSetting & {
    type: 'directory';
    value?: string;
};

export type ExtensionInfo = BaseExtensionSetting & {
    type: 'info';
    text: string;
};

export type ExtensionLink = BaseExtensionSetting & {
    type: 'link';
    url: string;
    linkLabel?: string;
};

export type ExtensionDivider = {
    type: 'divider';
};

export type ExtensionHeader = {
    type: 'header';
    label: string;
    description?: string;
};

export type ExtensionOption = {
    label: string;
    value: string;
    description?: string;
};

export type ExtensionSetting =
    | ExtensionToggle
    | ExtensionSlider
    | ExtensionText
    | ExtensionNumber
    | ExtensionSelect
    | ExtensionMultiSelect
    | ExtensionRadio
    | ExtensionColor
    | ExtensionButton
    | ExtensionDate
    | ExtensionTime
    | ExtensionRange
    | ExtensionFile
    | ExtensionDirectory
    | ExtensionInfo
    | ExtensionLink;

export type ExtensionSettingItem =
    | ExtensionSetting
    | ExtensionDivider
    | ExtensionHeader;
