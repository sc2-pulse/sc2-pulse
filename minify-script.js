const { minify } = require("terser");
const babel = require("@babel/core");
const fs = require('fs');
const path = require('path');

const inputDir = 'src/main/resources/static/script';
const outputDir = 'target/classes/static/script';
const outputFile = 'sc2-restful.min.js';
const orderedFiles = [
    'LuxonConfig.js',
    'IntervalExecutor.js',
    'RequestRateLimiter.js',
    'SortParameter.js',
    'Cursor.js',
    'TeamLegacyIdEntry.js',
    'TeamLegacyId.js',
    'TeamLegacyUid.js',
    'Util.js',
    'BootstrapUtil.js',
    'ElementUtil.js',
    'enum.js',
    'EnumUtil.js',
    'SC2PulseAPI.js',
    'MmrHistory.js',
    'CharacterUtil.js',
    'FollowUtil.js',
    'HistoryUtil.js',
    'LadderUtil.js',
    'Model.js',
    'Pagination.js',
    'PaginationUtil.js',
    'SeasonUtil.js',
    'MetaUtil.js',
    'Session.js',
    'SC2Restful.js',
    'ChartUtil.js',
    'StatsUtil.js',
    'TableUtil.js',
    'TeamUtil.js',
    'ViewUtil.js',
    'FormUtil.js',
    'ClanUtil.js',
    'Buffer.js',
    'BufferUtil.js',
    'MatchUtil.js',
    'VersusUtil.js',
    'VODUtil.js',
    'RevealUtil.js',
    'GroupUtil.js',
    'CommunityUtil.js',
    'MatrixUI.js',
    'EnhancementUtil.js'
];

let rawCombinedCode = '';
for (const file of orderedFiles) {
    const content = fs.readFileSync(path.join(inputDir, file), 'utf8');
    rawCombinedCode += `\n/* Source: ${file} */\n${content}\n`;
}
const babelResult = babel.transformSync(rawCombinedCode);
minify(babelResult.code, {
    compress: true,
    mangle: true
})
    .then(minified=>{
        if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });
        fs.writeFileSync(path.join(outputDir, outputFile), minified.code);
    })
    .catch(error => {
      console.error(error);
    });
