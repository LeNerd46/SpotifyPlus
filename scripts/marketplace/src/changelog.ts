import { MarketplaceChangelog, MarketplaceChangelogChange, MarketplaceChangelogRelease, MarketplaceChangelogSection } from './types/extension';

const isRecord = (value: unknown): value is Record<string, unknown> => (
    typeof value === 'object' && value !== null && !Array.isArray(value)
);

const requireString = (value: unknown, field: string) => {
    if (typeof value !== 'string' || value.trim().length === 0) {
        throw new Error(`${field} must be a non-empty string`);
    }

    return value.trim();
};

const parseChange = (value: unknown, field: string): MarketplaceChangelogChange => {
    if (typeof value === 'string') {
        return requireString(value, field);
    }

    if (!isRecord(value)) {
        throw new Error(`${field} must be a string or change object`);
    }

    const text = requireString(value.text, `${field}.text`);
    if (value.subLines !== undefined && !Array.isArray(value.subLines)) {
        throw new Error(`${field}.subLines must be an array`);
    }

    const subLines = value.subLines?.map((line, index) => (
        requireString(line, `${field}.subLines[${index}]`)
    ));

    return subLines?.length ? { text, subLines } : { text };
};

const parseSection = (value: unknown, field: string): MarketplaceChangelogSection => {
    if (!isRecord(value)) {
        throw new Error(`${field} must be an object`);
    }

    if (!Array.isArray(value.changes)) {
        throw new Error(`${field}.changes must be an array`);
    }

    return {
        heading: requireString(value.heading, `${field}.heading`),
        changes: value.changes.map((change, index) => (
            parseChange(change, `${field}.changes[${index}]`)
        ))
    };
};

const parseRelease = (value: unknown, field: string): MarketplaceChangelogRelease => {
    if (!isRecord(value)) {
        throw new Error(`${field} must be an object`);
    }

    if (!Array.isArray(value.sections)) {
        throw new Error(`${field}.sections must be an array`);
    }

    return {
        version: requireString(value.version, `${field}.version`),
        release: requireString(value.release, `${field}.release`),
        sections: value.sections.map((section, index) => (
            parseSection(section, `${field}.sections[${index}]`)
        ))
    };
};

export const parseMarketplaceChangelog = (value: unknown): MarketplaceChangelog => {
    if (!Array.isArray(value)) {
        throw new Error('Changelog must be an array of releases');
    }

    return value.map((release, index) => (
        parseRelease(release, `changelog[${index}]`)
    ));
};

export const formatReleaseDate = (release: string) => {
    const match = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(release);
    if (!match) {
        return release;
    }

    const [, year, month, day] = match;
    const date = new Date(Number(year), Number(month) - 1, Number(day));

    if (date.getFullYear() !== Number(year) || date.getMonth() !== Number(month) - 1 || date.getDate() !== Number(day)) {
        return release;
    }

    const monthNames = [
        'Jan',
        'Feb',
        'Mar',
        'Apr',
        'May',
        'Jun',
        'Jul',
        'Aug',
        'Sep',
        'Oct',
        'Nov',
        'Dec',
    ];

    return `${monthNames[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()}`;
};
