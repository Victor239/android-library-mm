// 1) HTML
window.addEventListener('submit', function(e) {
    interceptor(e);
}, true);

// 2) HTMLFormElement.prototype.submit
HTMLFormElement.prototype.submit = interceptor;

function interceptor(e) {
    var form = e ? e.target : this;
    var aa = [];
    for (i = 0; i < form.elements.length; i++) {
        var name = form.elements[i].name;
        var value = form.elements[i].value;
        aa.push({"name" : name, "value" : value});
    }
    interception.customSubmit(
            form.attributes['method'] === undefined ? null
                    : form.attributes['method'].nodeValue,
            form.attributes['action'] === undefined ? null
                    : form.attributes['action'].nodeValue,
            form.attributes['enctype'] === undefined ? null
                    : form.attributes['enctype'].nodeValue,
            JSON.stringify({"form" : aa}));
}

function EnableRW(obj, name) {
    var val = null;
	var prop = {}
	function getter() {
		return val;
	}
	function setter(v) {
		val = v;
	}
	Object.defineProperty(obj, name, {
	    get: getter,
	    set: setter,
	    configurable: true
	});
}

EnableRW(XMLHttpRequest.prototype, 'onreadystatechange');
EnableRW(XMLHttpRequest.prototype, 'responseURL');
EnableRW(XMLHttpRequest.prototype, 'responseXML');
EnableRW(XMLHttpRequest.prototype, 'status');
EnableRW(XMLHttpRequest.prototype, 'statusText');
EnableRW(XMLHttpRequest.prototype, 'readyState');

// 3) XMLHttpRequest.prototype.send
XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {
    this.params = {
      "method" : method === undefined ? null : method,
      "url" : url === undefined ? null : url,
      "async" : async === undefined ? null : async,
      "user" : user === undefined ? null : user,
      "password" : pass === undefined ? null : pass
    };
    this.header = {};
    this.setRequestHeader = function(name, value) {
        this.header[name] = value;
    }
    EnableRW(this, 'response');
    EnableRW(this, 'responseText');
};

XMLHttpRequest.prototype.send = function(form) {
    var params = this.params;
    var response = interception.customAjax(params.method, params.url, params.user, params.password, JSON.stringify(this.header), form);
    this.response = response;
    this.responseText = response;
    this.responseURL = params.url;
    this.responseXML = response;
    this.readyState = 4;
    this.status = 200;
    this.statusText = "OK";
    this.onreadystatechange && this.onreadystatechange();
}
