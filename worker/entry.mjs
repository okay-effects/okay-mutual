// THE HUB'S ENTRY (okay-serverless' Cloudflare template): the module Scala.js linked (`sbt dist` → dist/main.js) —
// the hub's app and its objects, `worker` — given its entry points here: the default export and the Durable Object
// classes: an account's, a node's directory, the hub's list of nodes
import { DurableObject } from "cloudflare:workers";
import { worker } from "./dist/main.js";

export class AccountObject extends DurableObject {
  fetch(request) { return worker.objectFetch("ACCOUNTS", this.ctx, this.env, request); }
  // a payment not yet taken by its receiver is carried again
  alarm() { return worker.objectAlarm("ACCOUNTS", this.ctx, this.env); }
}

export class DirectoryObject extends DurableObject {
  fetch(request) { return worker.objectFetch("NODE", this.ctx, this.env, request); }
}

export class HubObject extends DurableObject {
  fetch(request) { return worker.objectFetch("HUB", this.ctx, this.env, request); }
}

export default {
  fetch: (request, env, ctx) => worker.fetch(request, env, ctx),
};
