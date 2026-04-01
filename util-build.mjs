import fs from 'fs';
import path from 'path';
import * as babel from '@babel/core';
import { minify } from 'terser';

const inputDir = 'src/main/resources/static/script';
const inputFiles = [
    'enum.js',
    'Util.js',
    'EnumUtil.js',
    'Cursor.js',
    'SortParameter.js',
    'RequestRateLimiter.js',
    'SC2PulseAPI.js'
];
const outputDir = 'target/classes/static/script/shared';
const outputFile = 'sc2pulse-util.min.js';

if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });
let rawCombinedCode = '';
for (const file of inputFiles) {
    const content = fs.readFileSync(path.join(inputDir, file), 'utf8');
    rawCombinedCode += `\n/* Source: ${file} */\n${content}\n`;
}
const babelResult = babel.transformSync(rawCombinedCode, {
    presets: [
        ['@babel/preset-env', { modules: false }]
    ],
    plugins: [
    {
        visitor: {
            ClassDeclaration(path) {
                if (path.parent.type !== 'ExportNamedDeclaration') {
                    path.replaceWith(babel.types.exportNamedDeclaration(path.node));
                }
            },
            VariableDeclaration(path) {
                if (
                    path.node.kind === 'const' &&
                    path.parent.type === 'Program' &&
                    path.parent.type !== 'ExportNamedDeclaration'
                ) {
                    path.replaceWith(babel.types.exportNamedDeclaration(path.node));
                }
            }
        }
    }
    ]
});
const minified = await minify(babelResult.code, {
    compress: true,
    mangle: true
})
fs.writeFileSync(path.join(outputDir, outputFile), minified.code);
