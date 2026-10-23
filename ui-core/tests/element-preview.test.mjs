import test from 'node:test';
import assert from 'node:assert/strict';
import { createValidator } from '../scripts/validate.mjs';
test('element appearance queries accept drafts but reject arbitrary paths',async()=>{
  const {ajv}=await createValidator(),validate=ajv.getSchema('urn:ui-core:1.0:query');
  const query={messageType:'query',schemaVersion:'1.0',requestId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',workspaceId:'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',operation:'get_element_visual_preview',
    payload:{elementId:'cccccccc-cccc-4ccc-8ccc-cccccccccccc',changes:[{path:'/fields/textureTop',value:'copper'}]}};
  assert.equal(validate(query),true,JSON.stringify(validate.errors));
  query.payload.changes[0].path='/code'; assert.equal(validate(query),false);
});
