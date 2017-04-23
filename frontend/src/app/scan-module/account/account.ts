/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */

export class Account {
    url: string;
    username: string;
    password: string;
    accountType: AccountType;
    configurations: Array<EventConfiguration>;
    lastUpdate: Date;
    sslCert: string;


    getKey(): string {
        return this.username + "@" + this.url;
    }

    containsEvent(key: String) : boolean {
        return this.configurations.some(ec => ec.key == key);
    }

}

export class ScannedAccount {
    constructor(public url: string, public username: string, public password: string, public sslCert: string) {
    }
}

export class EventConfiguration {
    key: string;
    imageUrl: string;
    name: string;
    url: string;
    begin: Date;
    end: Date;
    oneDay: boolean;
    location: string;
    apiVersion: number;
}

export enum AccountType {
    STAFF,
    SPONSOR
}

export interface ImageContainer {
    image: string;
}

export class EventConfigurationSelection implements ImageContainer {
    public image: string;
    constructor(public eventConfiguration: EventConfiguration, public selected: boolean) { }
}

export class EventWithImage implements ImageContainer {
    constructor(public eventConfiguration: EventConfiguration, public image: string) {}
}

export class AccountResponse {
    constructor(private account: Account, private existing: boolean) { }

    getAccount() {
        return this.account;
    }

    isExisting() {
        return this.existing;
    }
}

export class AccountsArray {

    constructor(private accounts: Array<Account>) {}


    getAllAccounts(): Array<Account> {
        return this.accounts;
    }

    get(key: string):Maybe<Account> {
        try {
            return this.find(key).map(pair => pair.second);
        } catch(e) {
            console.log(e);
            return new Nothing<Account>();
        }
    }

    set(key: string, value: Account) :void {
        let existing = this.find(key);
        if(existing.isPresent()) {
            this.accounts[existing.value.first] = existing.value.second;
        } else {
            this.accounts.push(value);
        }
    }

    private find(key:string): Maybe<Pair<number, Account>> {
        let result = this.accounts.map((v, i) => new Pair(i, v)).filter(a => a.second.getKey() === key);
        if(result.length > 0) {
            return new Some(result[0]);
        }
        return new Nothing<Pair<number, Account>>();
    }
}

export class Pair<X,Y> {
    constructor(public first:X, public second:Y) {}
}

export interface Maybe<X> {
    value: X;
    isPresent(): boolean;
    orElse(other: () => X):X;
    map<Y>(mapper: (X) => Y): Maybe<Y>;
    ifPresent(consumer: (X) => void);
}

export class Nothing<X> implements Maybe<X> {
    public value: X = undefined;

    isPresent() :boolean {
        return false;
    }
    orElse(other: () => X):X {
        return other.apply(other);
    }
    map(mapper: (X) => any) :Maybe<any> {
        return this;
    }
    ifPresent(consumer: (X) => void) {}
}

export class Some<X> implements Maybe<X> {
    constructor(public value: X) {}

    isPresent() :boolean {
        return typeof this.value !== "undefined" && this.value !== null;
    }

    orElse(other:() => X): X {
        return this.value;
    }

    map<Y>(mapper: (X) => Y): Some<Y> {
        return new Some<Y>(mapper.apply(this, [this.value]));
    }

    ifPresent(consumer: (X) => void) {
        if(this.isPresent()) {
            consumer(this.value);
        }
    }
}
