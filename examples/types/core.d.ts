/**
 * Core type definitions for Assistance Package Tools
 * 
 * This file provides base type definitions for the JavaScript environment
 * available in package tools execution.
 */

/**
 * Tool call parameters object
 */
export interface ToolParams {
    [key: string]: string | number | boolean | object;
}

/**
 * Tool configuration for object-style calls
 */
export interface ToolConfig {
    type?: string;
    name: string;
    params?: ToolParams;
}

/**
 * Common result interfaces for structured data
 */
export interface BaseResult {
    success: boolean;
    error?: string;
}

/**
 * Basic result data types
 */
export interface StringResult extends BaseResult {
    data: string;
    toString(): string;
}

export interface BooleanResult extends BaseResult {
    data: boolean;
    toString(): string;
}

export interface NumberResult extends BaseResult {
    data: number;
    toString(): string;
}

/**
 * Generic tool result type
 */
export type ToolResult = StringResult | BooleanResult | NumberResult |
    (BaseResult & { data: any });

/**
 * Get return type for a specific tool name
 */
export type ToolReturnType<T extends string> = T extends keyof import('./tool-types').ToolResultMap
    ? import('./tool-types').ToolResultMap[T]
    : any;

// ============================================================================
// Tool Call Function Declarations
// ============================================================================

/**
 * Global function to call a tool and get a result
 * @returns A Promise with the tool result data of the appropriate type
 */
export declare function toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string>(toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string>(config: ToolConfig & { name: T }): Promise<ToolReturnType<T>>;
export declare function toolCall(toolName: string): Promise<any>;

/**
 * Global function to complete tool execution with a result
 * @param result - The result to return
 */
export declare function complete<T>(result: T): void;

/**
 * Native interface for direct calls to Android
 */
export namespace NativeInterface {
    /**
     * Call a tool synchronously (legacy method)
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     * @returns A JSON string representing a ToolResult object
     */
    function callTool(toolType: string, toolName: string, paramsJson: string): string;

    /**
     * Call a tool asynchronously
     * @param callbackId - Unique callback ID
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     * The callback will receive a ToolResult object
     */
    function callToolAsync(callbackId: string, toolType: string, toolName: string, paramsJson: string): void;

    /**
     * Set the result of script execution
     * @param result - Result string
     */
    function setResult(result: string): void;

    /**
     * Set an error for script execution
     * @param error - Error message
     */
    function setError(error: string): void;

    /**
     * Log informational message
     * @param message - Message to log
     */
    function logInfo(message: string): void;

    /**
     * Log error message
     * @param message - Error message to log
     */
    function logError(message: string): void;

    /**
     * Log debug message with data
     * @param message - Debug message
     * @param data - Debug data
     */
    function logDebug(message: string, data: string): void;

    /**
     * Report detailed JavaScript error
     * @param errorType - Error type
     * @param errorMessage - Error message
     * @param errorLine - Line number where error occurred
     * @param errorStack - Error stack trace
     */
    function reportError(errorType: string, errorMessage: string, errorLine: number, errorStack: string): void;
}

/**
 * Lodash-like utility library
 */
export declare const _: {
    isEmpty(value: any): boolean;
    isString(value: any): boolean;
    isNumber(value: any): boolean;
    isBoolean(value: any): boolean;
    isObject(value: any): boolean;
    isArray(value: any): boolean;
    forEach<T>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => void): any;
    map<T, R>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => R): R[];
};

/**
 * Data utilities
 */
export declare const dataUtils: {
    /**
     * Parse JSON string to object
     * @param jsonString - JSON string to parse
     */
    parseJson(jsonString: string): any;

    /**
     * Convert object to JSON string
     * @param obj - Object to stringify
     */
    stringifyJson(obj: any): string;

    /**
     * Format date to string
     * @param date - Date to format
     */
    formatDate(date?: Date | string): string;
};

/**
 * Module exports object for CommonJS-style exports
 */
export declare var exports: {
    [key: string]: any;
}; 